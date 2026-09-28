package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.CategoryDisplayDataLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.DataLoaderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * 카테고리 표시 로더의 배치·언어 규약(PERF-20260921).
 *
 * 인프라 배선이 아니라 **어떤 I/O가 몇 번 나가는지**를 고정한다 — 1건씩 부르던 것이
 * 이 슬라이스가 고친 결함이고, 되돌아가도 컴파일은 통과한다.
 */
class CategoryDisplayDataLoaderTest {

    private val spotQueryUseCase = mockk<SpotQueryUseCase>()
    private val loader = CategoryDisplayDataLoader(spotQueryUseCase)

    private fun environment(vararg keyContexts: Pair<Any, Any>): BatchLoaderEnvironment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
        .keyContexts(keyContexts.map { it.first }, keyContexts.map { it.second })
        .build()

    private fun display(code: String, label: String, iconUrl: String?) = CategoryDisplay(category = SpotCategory(code), label = label, iconUrl = iconUrl)

    @Test
    fun `키를 모아 유스케이스를 한 번만 부른다`() {
        // 이 로더가 존재하는 이유. 코드마다 setOf(it)으로 부르던 것이 종류 수만큼 쿼리를 냈다.
        val codes = setOf("RESTAURANT", "LODGING", "SHOPPING")
        every { spotQueryUseCase.categoryDisplays(any(), any()) } returns
            codes.associateWith { display(it, "label-$it", null) }

        loader.load(codes, environment(*codes.map { it to DomainLocale.KO }.toTypedArray()))

        verify(exactly = 1) { spotQueryUseCase.categoryDisplays(codes, DomainLocale.KO) }
    }

    @Test
    fun `keyContext의 언어를 그대로 넘긴다`() {
        // 언어가 어긋나면 오류가 아니라 다른 언어의 라벨이 나간다 — 조용한 오답이다.
        every { spotQueryUseCase.categoryDisplays(any(), any()) } returns emptyMap()

        loader.load(setOf("RESTAURANT"), environment("RESTAURANT" to DomainLocale.JA))

        verify { spotQueryUseCase.categoryDisplays(setOf("RESTAURANT"), DomainLocale.JA) }
    }

    @Test
    fun `유스케이스가 돌려준 표시 정보를 코드별로 그대로 싣는다`() {
        // 필드마다 다른 값을 둬 뒤바뀐 매핑이 통과하지 못하게 한다.
        every { spotQueryUseCase.categoryDisplays(any(), any()) } returns mapOf(
            "RESTAURANT" to display("RESTAURANT", "음식점", "https://cdn/restaurant.png"),
            "LODGING" to display("LODGING", "숙박", null),
        )

        val result = loader
            .load(setOf("RESTAURANT", "LODGING"), environment("RESTAURANT" to DomainLocale.KO))
            .toCompletableFuture()
            .join()

        assertEquals("음식점", result.getValue("RESTAURANT").label)
        assertEquals("https://cdn/restaurant.png", result.getValue("RESTAURANT").iconUrl)
        assertEquals("RESTAURANT", result.getValue("RESTAURANT").category.code)
        assertEquals("숙박", result.getValue("LODGING").label)
        assertEquals(null, result.getValue("LODGING").iconUrl)
        assertEquals("LODGING", result.getValue("LODGING").category.code)
    }

    @Test
    fun `미리 채운 코드는 조회하지 않는다`() {
        // availableSpotCategories가 구성 조회로 이미 읽어 온 값을 prime으로 심는다. 등록된 필드
        // 리졸버는 소스에 값이 있어도 언제나 실행되므로, 심지 않으면 칩 목록이 한 번 더 조회된다.
        every { spotQueryUseCase.categoryDisplays(any(), any()) } returns emptyMap()
        val dataLoader = DataLoaderFactory.newMappedDataLoader(loader)
        val primed = display("RESTAURANT", "음식점", "https://cdn/restaurant.png")
        dataLoader.prime("RESTAURANT", primed)

        val value = dataLoader.load("RESTAURANT", DomainLocale.KO)
        dataLoader.dispatch()

        assertEquals(primed, value.join())
        verify(exactly = 0) { spotQueryUseCase.categoryDisplays(any(), any()) }
    }

    @Test
    fun `채우지 않은 코드는 같은 배치에서 함께 조회된다`() {
        // prime이 나머지 키의 배치를 깨지 않는지 — 심은 것만 빠지고 한 번에 나가야 한다.
        every { spotQueryUseCase.categoryDisplays(any(), any()) } returns
            mapOf("LODGING" to display("LODGING", "숙박", null))
        val dataLoader = DataLoaderFactory.newMappedDataLoader(loader)
        dataLoader.prime("RESTAURANT", display("RESTAURANT", "음식점", null))

        dataLoader.load("RESTAURANT", DomainLocale.KO)
        val lodging = dataLoader.load("LODGING", DomainLocale.KO)
        dataLoader.dispatch()

        assertEquals("숙박", lodging.join().label)
        verify(exactly = 1) { spotQueryUseCase.categoryDisplays(setOf("LODGING"), DomainLocale.KO) }
    }

    @Test
    fun `언어가 실리지 않으면 폴백하지 않고 실패한다`() {
        // 기본 언어로 흘리면 JA 사용자에게 EN 라벨이 나가고, 그 오답은 어디서도 드러나지 않는다.
        val error = assertThrows(IllegalStateException::class.java) {
            loader.load(setOf("RESTAURANT"), environment())
        }

        assertEquals(true, requireNotNull(error.message).contains("keyContext"))
        verify(exactly = 0) { spotQueryUseCase.categoryDisplays(any(), any()) }
    }
}
