package org.around.ieuri.tourism.infrastructure

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.test.EnableDgsTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.SpotQueryResolver
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spotDetail **필드별 지연 조회**를 실제 GraphQL 실행으로 검증한다(SPEC-0003).
 *
 * 이 테스트가 존재하는 이유: "요청한 필드만 resolve된다"는 건 순수 단위 테스트로 증명할 수 없다.
 * 스키마·리졸버 배선·DGS 소스 전달(read DTO를 부모 소스로 넘기는 구조)이 실제로 맞물려야 성립하므로,
 * 쿼리를 실행해 **호출 횟수**로 확인한다.
 */
@EnableDgsTest
@SpringBootTest(
    classes = [
        SpotQueryResolver::class,
        SpotDetailFieldResolutionTest.Mocks::class,
    ],
)
class SpotDetailFieldResolutionTest {

    @Configuration
    class Mocks {
        @Bean fun useCase(): SpotQueryUseCase = mockk(relaxed = true)
        @Bean fun localeContext(): LocaleContext = LocaleContext().apply { locale = DomainLocale.KO }
        @Bean fun currentMemberProvider(): CurrentMemberProvider = mockk(relaxed = true)
    }

    @Autowired lateinit var executor: DgsQueryExecutor
    @Autowired lateinit var useCase: SpotQueryUseCase

    // 유스케이스 목은 스프링 컨텍스트에 공유되는 싱글턴이라 호출 기록이 테스트 간에 누적된다.
    // 호출 "횟수"로 지연 조회를 검증하는 테스트이므로 매번 초기화해야 한다.
    @BeforeTest
    fun resetMock() = clearMocks(useCase)

    private fun stubBody() {
        every { useCase.spotDetail("100", DomainLocale.KO) } returns SpotDetailView(
            id = "100",
            title = "경복궁",
            category = SpotCategory.TOURIST_SPOT,
            thumbnailImageUrl = "https://img/100.jpg",
            overview = "조선의 법궁",
            address = "서울 종로구",
            homepage = null,
            tel = null,
            copyright = CopyrightType.TYPE1,
            attributionText = CopyrightType.TYPE1.attributionText,
        )
    }

    @Test
    fun `본문 필드만 요청하면 지연 필드는 조회되지 않는다`() {
        stubBody()

        val title: String = executor.executeAndExtractJsonPath(
            """{ spotDetail(id: "100") { id title overview } }""",
            "data.spotDetail.title",
        )

        assertEquals("경복궁", title)
        // GraphQL의 본질 — 안 물어본 필드는 upstream/DB를 건드리지 않는다.
        verify(exactly = 0) { useCase.spotGallery(any(), any()) }
        verify(exactly = 0) { useCase.spotIntro(any(), any(), any()) }
        verify(exactly = 0) { useCase.spotInfoItems(any(), any(), any()) }
        verify(exactly = 0) { useCase.spotLikeCount(any(), any()) }
    }

    @Test
    fun `gallery를 선택하면 그때 갤러리를 조회한다`() {
        stubBody()
        every { useCase.spotGallery("100", DomainLocale.KO) } returns listOf(
            SpotImageView("https://img/g1.jpg", null, "야경", CopyrightType.TYPE3, CopyrightType.TYPE3.attributionText),
        )

        val urls: List<String> = executor.executeAndExtractJsonPath(
            """{ spotDetail(id: "100") { gallery { imageUrl } } }""",
            "data.spotDetail.gallery[*].imageUrl",
        )

        assertEquals(listOf("https://img/g1.jpg"), urls)
        verify(exactly = 1) { useCase.spotGallery("100", DomainLocale.KO) }
        verify(exactly = 0) { useCase.spotIntro(any(), any(), any()) }
    }

    @Test
    fun `intro는 필드 인자로 받은 category로 조회된다`() {
        stubBody()
        every { useCase.spotIntro("100", SpotCategory.RESTAURANT, DomainLocale.KO) } returns SpotIntroView(
            businessHours = "11:00~22:00",
            closedDays = null, parking = null, inquiryPhone = null,
            fee = null, petAllowed = null, creditCardAccepted = null,
        )

        val hours: String = executor.executeAndExtractJsonPath(
            """{ spotDetail(id: "100") { intro(category: RESTAURANT) { businessHours } } }""",
            "data.spotDetail.intro.businessHours",
        )

        assertEquals("11:00~22:00", hours)
        // 루트가 아니라 필드에 붙은 인자가 그대로 전달된다.
        verify(exactly = 1) { useCase.spotIntro("100", SpotCategory.RESTAURANT, DomainLocale.KO) }
    }

    @Test
    fun `intro가 값 없음이면 null로 응답한다`() {
        stubBody()
        every { useCase.spotIntro(any(), any(), any()) } returns null

        val result = executor.execute("""{ spotDetail(id: "100") { intro(category: TOURIST_SPOT) { parking } } }""")

        assertTrue(result.errors.isEmpty(), "errors: ${result.errors}")
        @Suppress("UNCHECKED_CAST")
        val detail = (result.getData<Map<String, Any?>>())["spotDetail"] as Map<String, Any?>
        assertNull(detail["intro"])
    }

    @Test
    fun `infoItems와 likeCount도 선택 시에만 조회된다`() {
        stubBody()
        every { useCase.spotInfoItems("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO) } returns
            listOf(SpotInfoItemView("입장료", "무료"))
        every { useCase.spotLikeCount("100", DomainLocale.KO) } returns 7L

        val likeCount: Int = executor.executeAndExtractJsonPath(
            """{ spotDetail(id: "100") { likeCount infoItems(category: TOURIST_SPOT) { name text } } }""",
            "data.spotDetail.likeCount",
        )

        assertEquals(7, likeCount)
        verify(exactly = 1) { useCase.spotInfoItems("100", SpotCategory.TOURIST_SPOT, DomainLocale.KO) }
        verify(exactly = 1) { useCase.spotLikeCount("100", DomainLocale.KO) }
        verify(exactly = 0) { useCase.spotGallery(any(), any()) }
    }
}
