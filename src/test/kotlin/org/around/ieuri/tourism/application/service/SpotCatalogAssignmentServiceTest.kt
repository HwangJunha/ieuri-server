package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.CatalogLanguage
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.VisibilityRefreshResult
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.event.SpotCatalogLanguagesChanged
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 카탈로그 배정(SPEC-0019 R-1·R-2) — 어떤 키로 찾고, 언제 붙이고 언제 새로 만드는가. */
class SpotCatalogAssignmentServiceTest {

    private val thumb = "https://img/1.jpg"
    private val mapX = 129.1234
    private val mapY = 35.5678

    private fun spot(
        spotId: Long = 11,
        language: DomainLocale = DomainLocale.EN,
        title: String = "Marine City (마린시티)",
        address: String? = "Busan",
        mapX: Double = this.mapX,
        mapY: Double = this.mapY,
    ) = UnassignedSpot(
        spotId = spotId,
        language = language,
        title = title,
        address = address,
        thumbnailImageUrl = thumb,
        mapX = mapX,
        mapY = mapY,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    private fun koreanSpot(spotId: Long = 12, title: String = "마린시티", mapX: Double = this.mapX) = spot(spotId = spotId, language = DomainLocale.KO, title = title, address = "부산", mapX = mapX)

    private fun service(
        query: StubQueryPort,
        store: RecordingStorePort,
        upsert: RecordingUpsertPort = RecordingUpsertPort(),
        events: RecordingEventPublisher = RecordingEventPublisher(),
    ) = SpotCatalogService(query, store, upsert, events)

    private class RecordingEventPublisher : TourismEventPublisherPort {
        val published = mutableListOf<Any>()

        override fun publish(event: Any) {
            published += event
        }
    }

    @Test
    @DisplayName("비한국어는 괄호에서 뽑은 한국어로 KO 카탈로그를 찾는다")
    fun `foreign row looks up by the extracted korean key`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY, thumb))),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 900L), store.assignments)
        assertEquals(1, result.joined)
        assertEquals(0, result.catalogsCreated)
    }

    @Test
    @DisplayName("제목이 달라도 썸네일이 같으면 KO에 붙는다")
    fun `thumbnail fallback links rows whose titles differ`() {
        // 언어별 제목이 달라도 좌표·썸네일이 같으면 붙는다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot(title = "韓華渡假村/龍仁(한화리조트 / 용인)")),
            thumbnailIndex = mapOf("https://img/1.jpg" to 900L),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 900L), store.assignments)
        assertEquals(0, result.catalogsCreated)
    }

    @Test
    @DisplayName("제목으로 찾으면 썸네일을 보지 않는다")
    fun `title match wins over thumbnail`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY, thumb))),
            thumbnailIndex = mapOf("https://img/1.jpg" to 777L),
        )

        service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 900L), store.assignments)
    }

    @Test
    @DisplayName("KO 짝이 없으면 자기 언어만 가진 카탈로그를 만든다")
    fun `foreign row without a korean counterpart creates its own catalog`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(spot(title = "SM Entertainment")))

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(1, result.catalogsCreated)
        assertEquals(listOf(11L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
        val saved = store.saved.single()
        assertEquals("SM Entertainment", saved.translations[DomainLocale.EN]?.title)
        assertNull(saved.translations[DomainLocale.KO], "KO 원본이 없으면 슬롯은 비어 있다")
    }

    @Test
    @DisplayName("병기된 한국어 괄호는 표시 제목에서 떼어낸다")
    fun `korean parenthetical is stripped from the stored title`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY, thumb))),
        )
        val upsert = RecordingUpsertPort()

        service(query, store, upsert).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(Triple(900L, DomainLocale.EN, "Marine City")), upsert.translations)
    }

    @Test
    @DisplayName("KO 제목은 그대로 저장한다")
    fun `korean title is stored as is`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot()))

        service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals("마린시티", store.saved.single().translations[DomainLocale.KO]?.title)
    }

    @Test
    @DisplayName("같은 이름의 카탈로그가 여럿이면 좌표가 일치하는 것에 붙인다")
    fun `ambiguous name is resolved by exact coordinates`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf(
                "마린시티" to listOf(
                    CatalogCandidate(900, 127.0, 37.5, thumb),
                    CatalogCandidate(901, mapX, mapY, thumb),
                ),
            ),
        )

        service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 901L), store.assignments)
    }

    @Test
    @DisplayName("좌표가 같아도 썸네일이 다르면 다른 장소다")
    fun `same coordinates but different thumbnail is a different place`() {
        // 제목 키·좌표가 같은 매장은 썸네일로만 구분된다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(koreanSpot()),
            koreanIndex = mapOf(
                "마린시티" to listOf(CatalogCandidate(900, mapX, mapY, "https://img/other.jpg")),
            ),
        )

        service(query, store).assignCatalogs(DomainLocale.KO)

        // 붙지 않고 새 카탈로그가 생긴다.
        assertEquals(1, store.saved.size)
        assertEquals(listOf(12L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
    }

    @Test
    @DisplayName("좌표와 썸네일이 모두 같으면 같은 장소로 접는다")
    fun `same coordinates and thumbnail folds into one catalog`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(koreanSpot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY, thumb))),
        )

        service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(0, store.saved.size)
        assertEquals(listOf(12L to 900L), store.assignments)
    }

    @Test
    @DisplayName("좌표로도 가릴 수 없으면 붙이지 않고 새로 만든다")
    fun `unresolvable ambiguity creates a new catalog`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf(
                "마린시티" to listOf(
                    CatalogCandidate(900, 127.0, 37.5, thumb),
                    CatalogCandidate(901, 128.0, 36.5, thumb),
                ),
            ),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(1, result.catalogsCreated)
        assertEquals(listOf(11L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
    }

    @Test
    @DisplayName("KO끼리는 같은 키·같은 좌표면 한 카탈로그로 접힌다")
    fun `korean rows fold within the same batch`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot(spotId = 12), koreanSpot(spotId = 13)))

        val result = service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(1, result.catalogsCreated)
        assertEquals(1, result.joined)
    }

    @Test
    @DisplayName("KO끼리도 좌표가 다르면 접지 않는다")
    fun `korean rows do not fold when coordinates differ`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(koreanSpot(spotId = 12), koreanSpot(spotId = 13, mapX = 127.0)),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(2, result.catalogsCreated)
    }

    @Test
    @DisplayName("새 카탈로그는 원본의 좌표·분류·저작권을 그대로 갖는다")
    fun `new catalog carries the source standard values`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot()))

        service(query, store).assignCatalogs(DomainLocale.KO)

        val standard = store.saved.single().standard
        assertEquals(129.1234, standard.mapX)
        assertEquals(35.5678, standard.mapY)
        assertEquals(thumb, standard.thumbnailImageUrl)
        assertEquals(SpotCategory.TOURIST_SPOT, standard.category)
        assertEquals(CopyrightType.TYPE1, standard.copyright)
    }

    @Test
    @DisplayName("새 카탈로그에는 축제 기간을 심는다")
    fun `newly created catalog gets its festival period planted`() {
        // 축제 기간은 spot_festival에서 온다.
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot()))
        val upsert = RecordingUpsertPort()

        service(query, store, upsert).assignCatalogs(DomainLocale.KO)

        assertEquals(listOf(RecordingStorePort.NEW_CATALOG_ID), upsert.festivalRefreshed)
    }

    @Test
    @DisplayName("KO도 회차를 넘는 중복을 접는다")
    fun `korean folds across chunk boundaries`() {
        // 회차 경계를 사이에 둔 같은 장소도 DB 맵으로 접힌다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(koreanSpot(spotId = 12)),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY, thumb))),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(listOf(12L to 900L), store.assignments)
        assertEquals(0, result.catalogsCreated, "이전 회차가 만든 카탈로그에 접혀야 한다")
    }

    @Test
    @DisplayName("건너뛴 행도 커서를 전진시킨다")
    fun `skipped rows still advance the cursor`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(spot(spotId = 42, title = "초려공방")))

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(1, result.skipped)
        assertEquals(0, result.assigned)
        assertEquals(1, result.processed, "processed가 0이면 언어를 소진한 것으로 오판한다")
        assertEquals(42L, result.lastSpotId, "커서가 전진해야 다음 회차가 앞으로 나아간다")
        assertTrue(store.assignments.isEmpty(), "제목이 없는 행은 배정하지 않는다")
    }

    @Test
    @DisplayName("대상이 없으면 커서를 그대로 둔다")
    fun `empty result keeps the cursor`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = emptyList())

        val result = service(query, store).assignCatalogs(DomainLocale.EN, afterSpotId = 77L)

        assertEquals(0, result.processed)
        assertEquals(77L, result.lastSpotId)
    }

    @Test
    @DisplayName("원본 생존 여부를 조회 노출에 반영한다")
    fun `catalog visibility follows the origin`() {
        // 번역 행을 지우지 않는다.
        val store = RecordingStorePort()
        val query = StubQueryPort()
        val upsert = RecordingUpsertPort()

        service(query, store, upsert).refreshCatalogVisibility()

        assertEquals(1, store.visibilityRefreshes)
    }

    @Test
    @DisplayName("뒤집힌 행이 없어도 노출 중인 전부를 이벤트로 알린다")
    fun `visibility event carries the full resolved set`() {
        // 이벤트는 델타가 아니라 정본을 싣는다. hidden·shown이 비어도 발행한다.
        val store = RecordingStorePort()
        val upsert = RecordingUpsertPort()
        store.visibilityResult = VisibilityRefreshResult(
            hiddenCount = 0,
            shownCount = 0,
            visible = listOf(
                CatalogLanguage(10L, DomainLocale.KO),
                CatalogLanguage(10L, DomainLocale.EN),
            ),
        )
        val events = RecordingEventPublisher()

        service(StubQueryPort(), store, upsert, events).refreshCatalogVisibility()

        val published = events.published.filterIsInstance<SpotCatalogLanguagesChanged>().single()
        assertEquals(
            listOf(
                CatalogLanguageRef(10L, "KO"),
                CatalogLanguageRef(10L, "EN"),
            ),
            published.resolved,
        )
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    fun `empty target set does nothing`() {
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = emptyList())

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(0, result.assigned)
        assertEquals(0, query.indexLoads, "대상이 없으면 맵도 읽지 않는다")
    }

    // ── 스텁 ────────────────────────────────────────────────────────────────

    private class StubQueryPort(
        private val unassigned: List<UnassignedSpot> = emptyList(),
        private val koreanIndex: Map<String, List<CatalogCandidate>> = emptyMap(),
        private val thumbnailIndex: Map<String, Long> = emptyMap(),
    ) : SpotCatalogQueryPort {
        var indexLoads = 0
            private set

        override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot> = unassigned.filter { it.language == language && it.spotId > afterSpotId }.take(limit)

        override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> {
            indexLoads++
            return koreanIndex
        }

        override fun loadKoreanThumbnailIndex(): Map<String, Long> = thumbnailIndex
    }

    private class RecordingStorePort : SpotCatalogStorePort {
        val saved = mutableListOf<SpotCatalog>()
        val assignments = mutableListOf<Pair<Long, Long>>()

        private var nextId = NEW_CATALOG_ID

        override fun save(catalog: SpotCatalog): Long {
            saved += catalog
            return nextId++
        }

        override fun assign(spotId: Long, catalogId: Long) {
            assignments += spotId to catalogId
        }

        var visibilityRefreshes = 0
            private set

        /** 갱신 결과. 기본값은 "뒤집힌 것은 없고 노출 중인 것은 있다"(전량 재적재 직후). */
        var visibilityResult = VisibilityRefreshResult(
            hiddenCount = 0,
            shownCount = 0,
            visible = listOf(CatalogLanguage(NEW_CATALOG_ID, DomainLocale.KO)),
        )

        override fun refreshTranslationVisibility(): VisibilityRefreshResult {
            visibilityRefreshes++
            return visibilityResult
        }

        companion object {
            const val NEW_CATALOG_ID = 7777L
        }
    }

    /** 생 SQL 경로 포트의 스텁. */
    private class RecordingUpsertPort : SpotCatalogUpsertPort {
        val translations = mutableListOf<Triple<Long, DomainLocale, String>>()
        val festivalRefreshed = mutableListOf<Long>()

        override fun upsertTranslation(
            catalogId: Long,
            language: DomainLocale,
            title: String,
            address: String?,
        ) {
            translations += Triple(catalogId, language, title)
        }

        override fun refreshFestivalPeriod(catalogId: Long) {
            festivalRefreshed += catalogId
        }
    }
}
