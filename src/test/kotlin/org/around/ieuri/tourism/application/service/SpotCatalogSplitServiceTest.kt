package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.persistence.CatalogLanguage
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogSplitPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.VisibilityRefreshResult
import org.around.ieuri.tourism.application.port.out.query.CatalogOrigin
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogSpatialQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 잘못 접힌 카탈로그 분할(SPEC-0029). 후보 판정은 스텁이 내주고, 서비스가 후보를 군집으로 가르는 방식을 본다. */
class SpotCatalogSplitServiceTest {

    /** `SpotCategory`에 이름 상수가 없는 코드라 직접 만든다. */
    private val restaurant = SpotCategory("RESTAURANT")

    // 실측 좌표. 두 지점은 247km 떨어져 있다.
    private val incheonX = 126.4158814558
    private val incheonY = 37.4393229658
    private val gwangjuX = 126.9500493009
    private val gwangjuY = 35.2538411163

    private fun origin(
        spotId: Long,
        language: DomainLocale,
        title: String = "카페 림",
        mapX: Double = incheonX,
        mapY: Double = incheonY,
        address: String? = "인천",
        thumbnailImageUrl: String = "https://img/incheon.jpg",
    ) = CatalogOrigin(
        spotId = spotId,
        language = language,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        mapX = mapX,
        mapY = mapY,
        category = restaurant,
        copyright = CopyrightType.TYPE3,
    )

    /** 인천 4행(KO·EN·JA·ZH) + 광주 3행(EN·JA·ZH). */
    private fun cafeLimOrigins() = listOf(
        origin(1, DomainLocale.KO),
        origin(2, DomainLocale.EN, title = "Cafe Rim (카페 림)"),
        origin(3, DomainLocale.JA, title = "カフェ・リム（카페 림）"),
        origin(4, DomainLocale.ZH, title = "Cafe Rim(카페 림)"),
        origin(5, DomainLocale.EN, title = "Café Limm (카페 림)", mapX = gwangjuX, mapY = gwangjuY, address = "광주"),
        origin(6, DomainLocale.JA, title = "カフェ林（카페 림）", mapX = gwangjuX, mapY = gwangjuY, address = "광주"),
        origin(7, DomainLocale.ZH, title = "LIMM(카페 림)", mapX = gwangjuX, mapY = gwangjuY, address = "광주"),
    )

    private fun service(
        spatial: StubSpatialQueryPort,
        store: RecordingStorePort = RecordingStorePort(),
        split: RecordingSplitPort = RecordingSplitPort(),
        upsert: RecordingUpsertPort = RecordingUpsertPort(),
    ) = SpotCatalogSplitService(spatial, store, split, upsert)

    @Test
    @DisplayName("원본이 4개를 넘고 10km 이상 떨어지면 군집대로 가른다")
    fun `folded catalog is split into its clusters`() {
        // `카페 림`(인천)과 `카페 림`(광주)이 제목 키가 같아 한 카탈로그로 접힌 경우.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val store = RecordingStorePort()
        val split = RecordingSplitPort()

        val result = service(spatial, store, split).splitMismatchedCatalogs()

        assertEquals(1, result.catalogsSplit)
        assertEquals(1, result.catalogsCreated, "군집이 둘이면 새 카탈로그는 하나다")
        assertEquals(3, result.spotsMoved, "광주 3행만 옮긴다")
        assertEquals(
            listOf(listOf(5L, 6L, 7L) to RecordingStorePort.NEW_CATALOG_ID),
            split.reassignments,
        )
    }

    @Test
    @DisplayName("원본이 4개를 넘어도 10km 안이면 가르지 않는다")
    fun `nearby origins are the same place and never split`() {
        // 후보로 들어와도 서비스가 다시 군집으로 확인한다 — 사슬처럼 이어진 점들은 한 군집이다.
        val nearby = (1L..6L).map { origin(it, DomainLocale.entries[(it % 4).toInt()], mapX = incheonX + it * 0.0001) }
        val spatial = StubSpatialQueryPort(candidates = listOf(300251L), origins = nearby)
        val store = RecordingStorePort()
        val split = RecordingSplitPort()

        val result = service(spatial, store, split).splitMismatchedCatalogs()

        assertEquals(0, result.catalogsSplit)
        assertEquals(0, result.catalogsCreated)
        assertTrue(store.saved.isEmpty(), "카탈로그를 만들지 않는다")
        assertTrue(split.reassignments.isEmpty(), "spot을 옮기지 않는다")
    }

    @Test
    @DisplayName("원본이 4개 이하면 아무리 멀어도 후보가 아니다")
    fun `four or fewer origins are never candidates`() {
        // 이 조건은 어댑터의 SQL이 강제하므로, 서비스에서는 후보를 받지 못하는 것으로 표현된다.
        val spatial = StubSpatialQueryPort(candidates = emptyList(), origins = cafeLimOrigins())
        val store = RecordingStorePort()
        val split = RecordingSplitPort()

        val result = service(spatial, store, split).splitMismatchedCatalogs()

        assertEquals(0, result.catalogsSplit)
        assertEquals(0, spatial.originLoads, "후보가 없으면 원본도 읽지 않는다")
        assertTrue(split.reassignments.isEmpty())
    }

    @Test
    @DisplayName("가장 큰 군집이 기존 카탈로그 id를 지킨다")
    fun `largest cluster keeps the original catalog id`() {
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val store = RecordingStorePort()
        val split = RecordingSplitPort()

        service(spatial, store, split).splitMismatchedCatalogs()

        // 인천 4행은 재배정 목록에 없다 — 그대로 295101에 남는다.
        val movedIds = split.reassignments.flatMap { it.first }
        assertEquals(listOf(5L, 6L, 7L), movedIds)
        assertTrue(movedIds.none { it in listOf(1L, 2L, 3L, 4L) }, "큰 군집은 움직이지 않는다")
    }

    @Test
    @DisplayName("KO가 없는 군집은 언어 순서상 첫 행에서 표준값을 가져온다")
    fun `cluster without a korean row takes standard values from the first by language order`() {
        // 광주 군집은 EN·JA·ZH뿐이라 EN이 대표가 된다. mapX·mapY는 개별로 단언한다.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val store = RecordingStorePort()

        service(spatial, store).splitMismatchedCatalogs()

        val created = store.saved.single()
        assertEquals(gwangjuX, created.standard.mapX)
        assertEquals(gwangjuY, created.standard.mapY)
        assertEquals(restaurant, created.standard.category)
        assertEquals(CopyrightType.TYPE3, created.standard.copyright)
        // EN이 대표라 EN 슬롯이 채워지고, 병기 한국어는 표시 제목에서 떨어진다.
        assertEquals("Café Limm", created.translations[DomainLocale.EN]?.title)
        assertEquals("광주", created.translations[DomainLocale.EN]?.address)
    }

    @Test
    @DisplayName("대표가 아닌 언어의 번역은 새 카탈로그에 덧붙인다")
    fun `remaining languages are upserted onto the new catalog`() {
        // 대표(EN)의 번역은 save가 이미 썼다.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val upsert = RecordingUpsertPort()

        service(spatial, upsert = upsert).splitMismatchedCatalogs()

        assertEquals(
            listOf(
                Triple(RecordingStorePort.NEW_CATALOG_ID, DomainLocale.JA, "カフェ林"),
                Triple(RecordingStorePort.NEW_CATALOG_ID, DomainLocale.ZH, "LIMM"),
            ),
            upsert.translations,
        )
    }

    @Test
    @DisplayName("옮겨 간 언어의 번역 행은 옛 카탈로그에서 지운다")
    fun `translations of departed languages are deleted from the old catalog`() {
        // 광주 군집은 EN·JA·ZH인데 인천에도 셋 다 있으므로, 옛 카탈로그가 잃는 언어는 없다.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val split = RecordingSplitPort()

        service(spatial, split = split).splitMismatchedCatalogs()

        assertTrue(split.deletions.isEmpty(), "남는 군집에도 있는 언어는 지우지 않는다")
    }

    @Test
    @DisplayName("남는 군집에 없는 언어만 옛 카탈로그에서 지운다")
    fun `only languages absent from the staying cluster are deleted`() {
        val origins = listOf(
            origin(1, DomainLocale.KO),
            origin(2, DomainLocale.EN, title = "Solomon (솔로몬)"),
            origin(3, DomainLocale.JA, title = "ソロモン（솔로몬）", mapX = gwangjuX, mapY = gwangjuY),
            origin(4, DomainLocale.ZH, title = "所羅門(솔로몬)", mapX = gwangjuX, mapY = gwangjuY),
            origin(5, DomainLocale.EN, title = "Solomon Daejeon (솔로몬)", mapX = gwangjuX, mapY = gwangjuY),
        )
        val spatial = StubSpatialQueryPort(candidates = listOf(293567L), origins = origins)
        val split = RecordingSplitPort()

        service(spatial, split = split).splitMismatchedCatalogs()

        // 큰 군집은 광주 3행(JA·ZH·EN), 나가는 것은 인천 2행(KO·EN). EN은 남는 쪽에도 있다.
        assertEquals(listOf(293567L to listOf(DomainLocale.KO)), split.deletions)
    }

    @Test
    @DisplayName("새 카탈로그에도 축제 기간을 심는다")
    fun `newly created catalog gets its festival period planted`() {
        // 기간은 저장한 행이 아니라 spot_festival에서 온다.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val upsert = RecordingUpsertPort()

        service(spatial, upsert = upsert).splitMismatchedCatalogs()

        assertEquals(listOf(RecordingStorePort.NEW_CATALOG_ID), upsert.festivalRefreshed)
    }

    @Test
    @DisplayName("군집이 셋이면 새 카탈로그도 둘이다")
    fun `three clusters produce two new catalogs`() {
        val origins = cafeLimOrigins() + listOf(
            origin(8, DomainLocale.EN, title = "Cafe Rim Jeju (카페 림)", mapX = 126.5, mapY = 33.5),
            origin(9, DomainLocale.ZH, title = "Cafe Rim Jeju(카페 림)", mapX = 126.5, mapY = 33.5),
        )
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = origins)
        val store = RecordingStorePort()

        val result = service(spatial, store).splitMismatchedCatalogs()

        assertEquals(1, result.catalogsSplit, "갈라진 옛 카탈로그는 하나다")
        assertEquals(2, result.catalogsCreated)
        assertEquals(5, result.spotsMoved)
    }

    @Test
    @DisplayName("후보가 없으면 아무것도 하지 않는다")
    fun `no candidates does nothing`() {
        val spatial = StubSpatialQueryPort(candidates = emptyList())
        val store = RecordingStorePort()

        val result = service(spatial, store).splitMismatchedCatalogs()

        assertEquals(0, result.catalogsSplit)
        assertEquals(0, result.catalogsCreated)
        assertEquals(0, result.spotsMoved)
    }

    // ── 스텁 ────────────────────────────────────────────────────────────────

    private class StubSpatialQueryPort(
        private val candidates: List<Long> = emptyList(),
        private val origins: List<CatalogOrigin> = emptyList(),
    ) : SpotCatalogSpatialQueryPort {
        var originLoads = 0
            private set

        override fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long> = candidates

        override fun findOrigins(catalogId: Long): List<CatalogOrigin> {
            originLoads++
            return origins
        }
    }

    private class RecordingStorePort : SpotCatalogStorePort {
        val saved = mutableListOf<SpotCatalog>()

        private var nextId = NEW_CATALOG_ID

        override fun save(catalog: SpotCatalog): Long {
            saved += catalog
            return nextId++
        }

        override fun assign(spotId: Long, catalogId: Long) = error("분할은 최초 배정을 부르지 않는다")

        override fun refreshTranslationVisibility(): VisibilityRefreshResult = VisibilityRefreshResult(0, 0, listOf(CatalogLanguage(NEW_CATALOG_ID, DomainLocale.KO)))

        companion object {
            const val NEW_CATALOG_ID = 8888L
        }
    }

    private class RecordingSplitPort : SpotCatalogSplitPort {
        val reassignments = mutableListOf<Pair<List<Long>, Long>>()
        val deletions = mutableListOf<Pair<Long, List<DomainLocale>>>()

        override fun reassign(spotIds: List<Long>, catalogId: Long) {
            reassignments += spotIds to catalogId
        }

        override fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>) {
            deletions += catalogId to languages
        }
    }

    private class RecordingUpsertPort : SpotCatalogUpsertPort {
        val translations = mutableListOf<Triple<Long, DomainLocale, String>>()
        val festivalRefreshed = mutableListOf<Long>()

        override fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?) {
            translations += Triple(catalogId, language, title)
        }

        override fun refreshFestivalPeriod(catalogId: Long) {
            festivalRefreshed += catalogId
        }
    }
}
