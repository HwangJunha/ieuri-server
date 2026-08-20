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

/**
 * 잘못 접힌 카탈로그 분할(SPEC-0029).
 *
 * 이 테스트가 고정하는 것은 **언제 가르고 언제 가르지 않는가**, 그리고 **가를 때 무엇이
 * 어디로 가는가**이다. 후보 판정 자체(원본 수 · 거리)는 생 SQL이라 어댑터에 있고 여기서는
 * 스텁이 후보를 내준다 — 대신 서비스가 그 후보를 어떻게 군집으로 가르는지를 본다.
 *
 * 좌표는 실측에서 가져온다: 인천 `카페 림`과 광주 `카페 림`이 247km, 광주 `솔로몬로파크`의
 * KO와 외국어 행이 1.6m 차이다.
 */
class SpotCatalogSplitServiceTest {

    /** 실측 카탈로그의 카테고리. `SpotCategory`에 이름 상수가 없는 코드라 직접 만든다. */
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

    /** 인천 4행(KO·EN·JA·ZH) + 광주 3행(EN·JA·ZH). 실측 카탈로그 295101의 모양이다. */
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
        // 실측 회귀: `카페 림`(인천)과 `카페 림`(광주)이 제목 키가 같아 한 카탈로그로 접혔다.
        // 매칭 키가 한글만 남기고, 비한국어 배정은 후보가 유일하면 좌표를 묻지 않는다.
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
        // 실측: 원본이 5~7개인데 300m 안에 모인 카탈로그가 6건 있다 — 상류가 같은 장소를
        // 중복 등록한 것이므로 가르면 오히려 한 장소가 둘로 쪼개진다.
        //
        // **후보 판정은 어댑터가 하지만 서비스도 다시 군집으로 확인한다** — 판정이 최대 거리로
        // 하는데 세 점이 사슬처럼 이어지면 최대 거리가 멀어도 한 군집이다.
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
        // 언어가 4개뿐이라 4개 이하는 아직 아무것도 접히지 않은 상태다. 거리만으로 판정하면
        // 한라산처럼 정당하게 넓은 장소가 걸린다(실측 32건).
        //
        // 이 조건은 어댑터의 SQL이 강제하므로, 서비스는 **후보를 받지 못하는 것**으로 표현된다.
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
        // **좋아요·글 연결이 기존 id에 매달려 있다.** 큰 쪽을 남겨야 옮기는 행이 적고 영향이 작다.
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
        // 갈라져 나온 광주 군집은 EN·JA·ZH뿐이다(실측). KO가 없으면 EN이 대표가 된다 —
        // `SpotCatalog.from`이 최초 배정에서 따르는 규칙과 같다.
        //
        // mapX·mapY가 둘 다 Double이라 뒤바뀌어도 컴파일된다. 값을 다르게 주고 개별로 단언한다.
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
        // 대표(EN)의 번역은 save가 이미 썼다. JA·ZH가 빠지면 그 언어 사용자에게 장소가 사라진다.
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
        // **감춤이 아니라 삭제다.** 감춤은 "원본이 잠시 사라졌다"는 뜻이라 원본이 돌아오면
        // 되살아나는데, 여기서 원본은 사라진 것이 아니라 다른 카탈로그로 갔다 — 되살릴 근거가
        // 없어 노출 갱신이 정리하지 못한다.
        //
        // 광주 군집은 EN·JA·ZH인데 인천에도 셋 다 있으므로, 옛 카탈로그가 잃는 언어는 없다.
        val spatial = StubSpatialQueryPort(candidates = listOf(295101L), origins = cafeLimOrigins())
        val split = RecordingSplitPort()

        service(spatial, split = split).splitMismatchedCatalogs()

        assertTrue(split.deletions.isEmpty(), "남는 군집에도 있는 언어는 지우지 않는다")
    }

    @Test
    @DisplayName("남는 군집에 없는 언어만 옛 카탈로그에서 지운다")
    fun `only languages absent from the staying cluster are deleted`() {
        // 실측 카탈로그 293567(`솔로몬로파크`)의 모양이다 — 광주 4행(KO·EN·JA·ZH)이 남고
        // 대전 2행(EN·ZH)이 나간다. EN·ZH는 광주에도 있으므로 지울 것이 없다.
        //
        // 여기서는 대전에만 있는 언어(JA)를 만들어 그 경우를 고정한다.
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
        // 기간은 저장한 행이 아니라 spot_festival에서 온다. 빠뜨리면 갈라져 나온 축제는
        // 기간을 영영 갖지 못한다 — 최초 배정과 같은 규칙이다.
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

        override fun refreshTranslationVisibility(): VisibilityRefreshResult =
            VisibilityRefreshResult(0, 0, listOf(CatalogLanguage(NEW_CATALOG_ID, DomainLocale.KO)))

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
