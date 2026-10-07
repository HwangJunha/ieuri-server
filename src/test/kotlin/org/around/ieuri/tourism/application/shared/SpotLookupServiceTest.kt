package org.around.ieuri.tourism.application.shared

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.catalog.required.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.category.provided.dto.AppCategoryView
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** SpotLookupService — 다른 도메인이 쓰는 문자열 축 조회. */
class SpotLookupServiceTest {

    private val spotQueryPort = mockk<SpotQueryPort>()
    private val appCategoryQueryPort = mockk<AppCategoryQueryPort>()
    private val service = SpotLookupService(spotQueryPort, appCategoryQueryPort)

    /** 다른 도메인이 쓰는 문자열 경로. */
    @Test
    fun `availableCategoryCodes는 문자열 축으로 같은 구성을 돌려준다`() {
        val appKey = AppKey("k-trip-spot")
        every { appCategoryQueryPort.findCategories(appKey, DomainLocale.JA) } returns
            listOf(AppCategoryView(SpotCategory("TRANSPORT"), "交通", "https://cdn/transport.png"))

        val result = service.availableCategoryCodes(appKey, "JA")

        assertEquals(listOf("TRANSPORT"), result.map { it.category })
        assertEquals(listOf("交通"), result.map { it.label })
        assertEquals(listOf("https://cdn/transport.png"), result.map { it.iconUrl })
    }

    /** 필드마다 다른 값 — 경도·위도가 뒤바뀌면 실패한다. */
    private fun basics() = SpotBasics(
        title = "경복궁",
        address = "서울 종로구",
        thumbnailImageUrl = "https://img/100.jpg",
        category = SpotCategory("TOURIST_SPOT"),
        copyright = CopyrightType.TYPE1,
        longitude = 126.977,
        latitude = 37.579,
    )

    @Test
    fun `spotSummary는 카탈로그 id와 spot 행 값을 필드별로 옮긴다`() {
        every { spotQueryPort.findSpotBasics(100L, DomainLocale.JA) } returns basics()

        val summary = service.spotSummary(100L, "JA")

        assertEquals(100L, summary?.id)
        assertEquals("경복궁", summary?.title)
        assertEquals("https://img/100.jpg", summary?.thumbnailImageUrl)
        assertEquals("TOURIST_SPOT", summary?.categoryCode)
        assertEquals(126.977, summary?.longitude)
        assertEquals(37.579, summary?.latitude)
    }

    @Test
    fun `spotSummary는 그 언어에 없으면 다른 언어로 대신하지 않고 null이다`() {
        every { spotQueryPort.findSpotBasics(100L, DomainLocale.EN) } returns null

        assertNull(service.spotSummary(100L, "EN"))
    }

    @Test
    fun `spotSummaries는 해소된 id만 키로 두고 같은 변환을 쓴다`() {
        every { spotQueryPort.findSpotBasicsIn(listOf(100L, 200L), DomainLocale.KO) } returns mapOf(200L to basics())

        val summaries = service.spotSummaries(listOf(100L, 200L), "KO")

        assertEquals(setOf(200L), summaries.keys)
        assertEquals(200L, summaries[200L]?.id)
        assertEquals(126.977, summaries[200L]?.longitude)
        assertEquals(37.579, summaries[200L]?.latitude)
    }
}
