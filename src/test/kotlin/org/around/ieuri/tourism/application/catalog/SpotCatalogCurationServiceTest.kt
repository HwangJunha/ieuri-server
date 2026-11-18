package org.around.ieuri.tourism.application.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.application.catalog.required.dto.CatalogLanguage
import org.around.ieuri.tourism.application.catalog.required.dto.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogFestival
import org.around.ieuri.tourism.domain.catalog.CatalogMembership
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.catalog.CatalogSplit
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.catalog.OriginFestivalPeriod
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.catalog.event.SpotCatalogLanguagesChanged
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 카탈로그 정비의 순서 — 판정은 도메인 테스트가 맡고, 여기서는 무엇을 어떤 순서로 쓰는지만 본다. */
class SpotCatalogCurationServiceTest {

    private val repository = mockk<SpotCatalogRepository>(relaxed = true)
    private val queryPort = mockk<SpotCatalogQueryPort>()
    private val events = mockk<CatalogEventPublisherPort>(relaxed = true)
    private val service = SpotCatalogCurationService(repository, queryPort, events)

    private fun origin(spotId: Long, language: DomainLocale, title: String, mapX: Double = 129.1, mapY: Double = 35.1) = CatalogOrigin(
        spotId = spotId,
        language = language,
        title = title,
        address = null,
        thumbnailImageUrl = "https://img/$spotId.jpg",
        mapX = mapX,
        mapY = mapY,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    private val koreanCatalog = SpotCatalog(
        id = 900,
        standard = CatalogStandardValues(129.1, 35.1, "https://img/1.jpg", SpotCategory.TOURIST_SPOT, CopyrightType.TYPE1),
        translations = listOf(CatalogTranslation(DomainLocale.KO, "마린시티", null, visible = true)),
    )

    @Test
    @DisplayName("기존 카탈로그에 붙을 때는 번역을 쓰고 원본을 배정한다")
    fun `join writes the new translation and assigns the origin`() {
        every { queryPort.findUnassigned(DomainLocale.EN, 0, any()) } returns listOf(origin(11, DomainLocale.EN, "Marine City (마린시티)"))
        every { queryPort.loadKoreanTitleIndex() } returns mapOf("마린시티" to listOf(CatalogCandidate(900, 127.0, 37.0, "x")))
        every { queryPort.loadKoreanThumbnailIndex() } returns emptyMap()
        every { repository.findById(900) } returns koreanCatalog

        val result = service.assignCatalogs(DomainLocale.EN, afterSpotId = 0)

        verify { repository.saveTranslation(koreanCatalog, CatalogTranslation(DomainLocale.EN, "Marine City", null, visible = true)) }
        verify { repository.saveMembership(CatalogMembership.Join(900, 11)) }
        verify(exactly = 0) { repository.insert(any()) }
        assertEquals(1, result.joined)
        assertEquals(11L, result.lastSpotId)
    }

    @Test
    @DisplayName("이미 있는 언어로 붙으면 번역을 쓰지 않고 배정만 한다")
    fun `join in an existing language only assigns`() {
        every { queryPort.findUnassigned(DomainLocale.KO, 0, any()) } returns listOf(origin(12, DomainLocale.KO, "마린시티"))
        every { queryPort.loadKoreanTitleIndex() } returns mapOf("마린시티" to listOf(CatalogCandidate(900, 129.1, 35.1, "https://img/12.jpg")))
        every { repository.findById(900) } returns koreanCatalog

        service.assignCatalogs(DomainLocale.KO, afterSpotId = 0)

        verify(exactly = 0) { repository.saveTranslation(any(), any()) }
        verify { repository.saveMembership(CatalogMembership.Join(900, 12)) }
    }

    @Test
    @DisplayName("새 카탈로그는 저장 → 원본 이동 → 축제 기간 순으로 쓴다")
    fun `found inserts, moves the origin, then sets the festival period`() {
        val period = FestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
        every { queryPort.findUnassigned(DomainLocale.KO, 5, any()) } returns listOf(origin(12, DomainLocale.KO, "부산불꽃축제"))
        every { queryPort.loadKoreanTitleIndex() } returns emptyMap()
        every { repository.insert(any()) } returns 500
        every { repository.findById(500) } returns koreanCatalog.copy(id = 500)
        every { repository.findOriginFestivalPeriods(500) } returns listOf(OriginFestivalPeriod(12, DomainLocale.KO, period))

        val result = service.assignCatalogs(DomainLocale.KO, afterSpotId = 5)

        verifyOrder {
            repository.insert(match { it.translations.single().title == "부산불꽃축제" })
            repository.saveMembership(CatalogMembership.Join(500, 12))
            repository.findOriginFestivalPeriods(500)
            repository.saveFestival(CatalogFestival(500, period))
        }
        assertEquals(1, result.catalogsCreated)
    }

    @Test
    @DisplayName("건너뛴 원본도 커서를 전진시키고, 대상이 없으면 커서를 그대로 둔다")
    fun `skipped origins advance the cursor`() {
        every { queryPort.findUnassigned(DomainLocale.EN, 0, any()) } returns listOf(origin(21, DomainLocale.EN, "마린시티"))
        every { queryPort.loadKoreanTitleIndex() } returns emptyMap()
        every { queryPort.loadKoreanThumbnailIndex() } returns emptyMap()
        every { queryPort.findUnassigned(DomainLocale.EN, 21, any()) } returns emptyList()

        val first = service.assignCatalogs(DomainLocale.EN, afterSpotId = 0)
        val second = service.assignCatalogs(DomainLocale.EN, afterSpotId = 21)

        assertEquals(1, first.skipped)
        assertEquals(21L, first.lastSpotId)
        assertEquals(0, second.processed)
        assertEquals(21L, second.lastSpotId)
    }

    @Test
    @DisplayName("KO 배정에는 썸네일 색인을 읽지 않는다")
    fun `korean assignment does not load the thumbnail index`() {
        every { queryPort.findUnassigned(DomainLocale.KO, 0, any()) } returns listOf(origin(12, DomainLocale.KO, "부산타워"))
        every { queryPort.loadKoreanTitleIndex() } returns emptyMap()
        every { repository.insert(any()) } returns 500
        every { repository.findById(500) } returns koreanCatalog.copy(id = 500)
        every { repository.findOriginFestivalPeriods(500) } returns emptyList()

        service.assignCatalogs(DomainLocale.KO, afterSpotId = 0)

        verify(exactly = 0) { queryPort.loadKoreanThumbnailIndex() }
        verify { repository.saveFestival(CatalogFestival(500, null)) }
    }

    @Test
    @DisplayName("분할은 떠나는 군집마다 새 카탈로그를 만들고 남는 쪽에 없는 언어 번역을 지운다")
    fun `split founds departures and removes departed languages`() {
        val near = 129.1
        val far = 128.0
        every { queryPort.findSplitCandidates(CatalogSplit.MIN_ORIGIN_COUNT, CatalogSplit.MIN_DISTANCE_METERS) } returns listOf(900)
        every { repository.findById(900) } returns koreanCatalog
        every { queryPort.findOrigins(900) } returns listOf(
            origin(1, DomainLocale.KO, "마린시티", mapX = near),
            origin(2, DomainLocale.EN, "Marine City", mapX = near),
            origin(3, DomainLocale.JA, "マリンシティ", mapX = far),
        )
        every { repository.insert(any()) } returns 501
        every { repository.findById(501) } returns koreanCatalog.copy(id = 501)
        every { repository.findOriginFestivalPeriods(501) } returns emptyList()

        val result = service.splitMismatchedCatalogs()

        verify { repository.saveMembership(CatalogMembership.Move(501, listOf(3L))) }
        verify { repository.deleteTranslations(900, listOf(DomainLocale.JA)) }
        assertEquals(1, result.catalogsSplit)
        assertEquals(1, result.catalogsCreated)
        assertEquals(1, result.spotsMoved)
    }

    @Test
    @DisplayName("한 군집이면 아무것도 쓰지 않는다")
    fun `split of one cluster writes nothing`() {
        every { queryPort.findSplitCandidates(any(), any()) } returns listOf(900)
        every { repository.findById(900) } returns koreanCatalog
        every { queryPort.findOrigins(900) } returns listOf(origin(1, DomainLocale.KO, "마린시티"), origin(2, DomainLocale.EN, "Marine City"))

        val result = service.splitMismatchedCatalogs()

        verify(exactly = 0) { repository.insert(any()) }
        verify(exactly = 0) { repository.deleteTranslations(any(), any()) }
        assertEquals(0, result.catalogsSplit)
    }

    @Test
    @DisplayName("노출 갱신 뒤 노출 중인 전부를 언어 코드 문자열로 알린다")
    fun `visibility refresh publishes the full visible set`() {
        every { repository.refreshTranslationVisibility() } returns VisibilityRefreshResult(
            hiddenCount = 0,
            shownCount = 0,
            visible = listOf(CatalogLanguage(900, DomainLocale.KO), CatalogLanguage(901, DomainLocale.EN)),
        )
        val event = slot<Any>()
        every { events.publish(capture(event)) } returns Unit

        service.refreshCatalogVisibility()

        assertEquals(SpotCatalogLanguagesChanged(listOf(CatalogLanguageRef(900, "KO"), CatalogLanguageRef(901, "EN"))), event.captured)
    }

    @Test
    @DisplayName("시군구 귀속은 붙인 수와 남은 수를 함께 돌려준다")
    fun `district assignment reports assigned and remaining`() {
        every { repository.assignUnassignedDistricts() } returns 3
        every { repository.countDistrictUnassigned() } returns 40

        val result = service.assignDistricts()

        assertEquals(3, result.assigned)
        assertEquals(40, result.unassigned)
    }
}
