package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 카탈로그 배정(SPEC-0019 R-1·R-2).
 *
 * 매칭은 **메모리 맵 대조**다 — KO 제목 키 맵을 한 번 읽고 비한국어가 괄호에서 뽑은 한국어로
 * 조회한다. 이 테스트가 고정하는 것은 *어떤 키로 무엇을 찾는가*와 *언제 붙이고 언제 새로
 * 만드는가*이다.
 */
class SpotCatalogAssignmentServiceTest {

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
        thumbnailImageUrl = "https://img/1.jpg",
        mapX = mapX,
        mapY = mapY,
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    private fun koreanSpot(spotId: Long = 12, title: String = "마린시티", mapX: Double = this.mapX) =
        spot(spotId = spotId, language = DomainLocale.KO, title = title, address = "부산", mapX = mapX)

    private fun service(query: StubQueryPort, store: RecordingStorePort) =
        SpotCatalogService(query, store, NoEvents)

    @Test
    @DisplayName("비한국어는 괄호에서 뽑은 한국어로 KO 카탈로그를 찾는다")
    fun `foreign row looks up by the extracted korean key`() {
        // **회귀 방어의 핵심.** 제목 전체를 정규화한 `MarineCity(마린시티)`로 찾으면 KO의
        // `마린시티`와 결코 일치하지 않아 매칭이 전량 실패한다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY))),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 900L), store.assignments)
        assertEquals(1, result.joined)
        assertEquals(0, result.catalogsCreated)
    }

    @Test
    @DisplayName("제목이 달라도 썸네일이 같으면 KO에 붙는다")
    fun `thumbnail fallback links rows whose titles differ`() {
        // 회귀: TourAPI가 같은 장소의 언어별 제목을 다르게 준다 —
        // `한화리조트 용인 베잔송`(KO) vs `한화리조트 / 용인`(ZH). 좌표·썸네일은 같다.
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
        // 제목이 더 강한 신호다. 썸네일은 제목이 실패했을 때의 폴백이다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY))),
            thumbnailIndex = mapOf("https://img/1.jpg" to 777L),
        )

        service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 900L), store.assignments)
    }

    @Test
    @DisplayName("KO 짝이 없으면 자기 언어만 가진 카탈로그를 만든다")
    fun `foreign row without a korean counterpart creates its own catalog`() {
        // 한국어명을 지어내지 않는다 — 원본에 없는 이름을 만들면 정합성이 깨진다.
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(spot(title = "SM Entertainment")))

        val result = service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(1, result.catalogsCreated)
        assertEquals(listOf(11L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
        val saved = store.saved.single()
        assertEquals("SM Entertainment", saved.translations[DomainLocale.EN]?.title)
        assertTrue(saved.needsTranslation(DomainLocale.KO), "KO는 비어 있어야 번역 대상이 된다")
    }

    @Test
    @DisplayName("병기된 한국어 괄호는 표시 제목에서 떼어낸다")
    fun `korean parenthetical is stripped from the stored title`() {
        // 괄호 한국어는 매칭 단서일 뿐이다. 남기면 일본어 사용자에게 한글이 그대로 노출된다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY))),
        )

        service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(Triple(900L, DomainLocale.EN, "Marine City")), store.translations)
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
        // `롯데마트`처럼 같은 이름이 전국에 여럿이라 제목만으로는 정할 수 없다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf(
                "마린시티" to listOf(
                    CatalogCandidate(900, 127.0, 37.5),
                    CatalogCandidate(901, mapX, mapY),
                ),
            ),
        )

        service(query, store).assignCatalogs(DomainLocale.EN)

        assertEquals(listOf(11L to 901L), store.assignments)
    }

    @Test
    @DisplayName("좌표로도 가릴 수 없으면 붙이지 않고 새로 만든다")
    fun `unresolvable ambiguity creates a new catalog`() {
        // 잘못 붙이면 좋아요가 다른 장소로 딸려 간다. 새로 만드는 편이 안전하다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(spot()),
            koreanIndex = mapOf(
                "마린시티" to listOf(
                    CatalogCandidate(900, 127.0, 37.5),
                    CatalogCandidate(901, 128.0, 36.5),
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
        // 같은 장소가 KO 안에서만 둘로 갈리면 좋아요가 따로 쌓인다 — 이 스펙이 없애려는 결함이다.
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
        // mapX·mapY가 둘 다 Double이라 서로 뒤바뀌어도 컴파일된다 — 뒤바뀌면 전 국토가
        // 지도 밖으로 나간다. 값을 다르게 주고 개별로 단언한다.
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot()))

        service(query, store).assignCatalogs(DomainLocale.KO)

        val standard = store.saved.single().standard
        assertEquals(129.1234, standard.mapX)
        assertEquals(35.5678, standard.mapY)
        assertEquals("https://img/1.jpg", standard.thumbnailImageUrl)
        assertEquals(SpotCategory.TOURIST_SPOT, standard.category)
        assertEquals(CopyrightType.TYPE1, standard.copyright)
    }

    @Test
    @DisplayName("새 카탈로그에는 축제 기간을 심는다")
    fun `newly created catalog gets its festival period planted`() {
        // 축제 기간은 저장한 행이 아니라 spot_festival에서 온다. 여기서 빠뜨리면 영영 비어 있다.
        val store = RecordingStorePort()
        val query = StubQueryPort(unassigned = listOf(koreanSpot()))

        service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(listOf(RecordingStorePort.NEW_CATALOG_ID), store.festivalRefreshed)
    }

    @Test
    @DisplayName("KO도 회차를 넘는 중복을 접는다")
    fun `korean folds across chunk boundaries`() {
        // 회귀: 회차가 200건씩 끊기므로 경계를 사이에 둔 같은 장소는 회차 한정 맵으로는
        // 못 접고 카탈로그가 둘로 갈린다. KO도 DB 맵을 읽어야 한다.
        val store = RecordingStorePort()
        val query = StubQueryPort(
            unassigned = listOf(koreanSpot(spotId = 12)),
            koreanIndex = mapOf("마린시티" to listOf(CatalogCandidate(900, mapX, mapY))),
        )

        val result = service(query, store).assignCatalogs(DomainLocale.KO)

        assertEquals(listOf(12L to 900L), store.assignments)
        assertEquals(0, result.catalogsCreated, "이전 회차가 만든 카탈로그에 접혀야 한다")
    }

    @Test
    @DisplayName("건너뛴 행도 커서를 전진시킨다")
    fun `skipped rows still advance the cursor`() {
        // 회귀: 건너뛴 행은 catalog_id가 null로 남는다. 커서가 안 움직이면 다음 회차가
        // 같은 행을 다시 읽어 영원히 제자리다.
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
    @DisplayName("고아 카탈로그는 번역을 지워 조회에서 감춘다")
    fun `orphan catalogs are hidden by purging translations`() {
        // 카탈로그와 좋아요는 남긴다 — 상류가 그 장소를 다시 주면 배정이 번역을 되살린다.
        val store = RecordingStorePort()
        val query = StubQueryPort()

        service(query, store).hideOrphanCatalogs()

        assertEquals(1, store.orphanPurges)
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

        override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot> =
            unassigned.filter { it.language == language && it.spotId > afterSpotId }.take(limit)

        override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> {
            indexLoads++
            return koreanIndex
        }

        override fun loadKoreanThumbnailIndex(): Map<String, Long> = thumbnailIndex

        override fun findCatalogsMissingTranslation(
            languages: Set<DomainLocale>,
            limit: Int,
        ): List<UntranslatedCatalog> = emptyList()
    }

    private class RecordingStorePort : SpotCatalogStorePort {
        val saved = mutableListOf<SpotCatalog>()
        val assignments = mutableListOf<Pair<Long, Long>>()
        val translations = mutableListOf<Triple<Long, DomainLocale, String>>()
        val festivalRefreshed = mutableListOf<Long>()

        private var nextId = NEW_CATALOG_ID

        override fun save(catalog: SpotCatalog): Long {
            saved += catalog
            return nextId++
        }

        override fun assign(spotId: Long, catalogId: Long) {
            assignments += spotId to catalogId
        }

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

        var orphanPurges = 0
            private set

        override fun purgeOrphanTranslations(): Int {
            orphanPurges++
            return 0
        }

        companion object {
            const val NEW_CATALOG_ID = 7777L
        }
    }

    private object NoEvents : TourismEventPublisherPort {
        override fun publish(event: Any) = Unit
    }
}
