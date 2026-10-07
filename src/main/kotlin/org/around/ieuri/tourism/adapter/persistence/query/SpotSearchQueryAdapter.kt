package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.required.SpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.SpotSearchQuery
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * BM25 전문 검색 어댑터(SPEC-0013, SPEC-0019). pg_search·PostGIS 함수 때문에 생 SQL이다.
 * - 메인 DB가 아니라 검색 노드(searchJdbcTemplate)를 본다.
 * - 언어 격리는 `language = ?` 조건이 전담한다 — 빠뜨리면 다른 언어 행이 섞인다(AC-2.3).
 * - `spot_catalog_translation` 단독 평면 질의다. 조인하면 `LIMIT` 조기 종료를 잃고 매칭 집합 전체를 읽는다.
 * - 썸네일·분류·저작권은 [loadCards]가 한 페이지분만 2단계로 읽는다.
 * - 검색과 카운트는 같은 조건을 써야 한다 — 갈리면 목록과 건수가 어긋난다.
 */
@Component
class SpotSearchQueryAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : SpotSearchPort {

    /** 관련도(거리 가중) 내림차순 + id 오름차순 한 페이지. 다음 페이지 판정은 호출자가 [count]로 한다. */
    override fun search(query: SpotSearchQuery): List<SpotView> {
        val strict = runSearch(query, fuzzyOnly = false)
        // 첫 페이지의 정확 일치가 0건일 때만 오타 검색으로 폴백한다([matchClause] 참조).
        if (strict.isNotEmpty() || query.offset > 0) return strict
        return runSearch(query, fuzzyOnly = true)
    }

    private fun runSearch(query: SpotSearchQuery, fuzzyOnly: Boolean): List<SpotView> {
        val (where, whereParams) = whereClause(query, fuzzyOnly)
        // 파라미터는 `?` 등장 순서다 — SELECT의 CENTER 두 개가 WHERE 몫보다 먼저다.
        val params = mutableListOf<Any>(
            query.center.longitude,
            query.center.latitude, // ST_Distance의 CENTER
            query.center.longitude,
            query.center.latitude, // relevance 감쇠항의 CENTER
        )
        params.addAll(whereParams)

        params += query.limit
        params += query.offset

        // 평면 쿼리여야 한다 — 서브쿼리·CTE·조인으로 감싸면 `@@@`가 `query is incompatible`로 실패한다.
        // 같은 이유로 커서 seek는 불가(`pdb.score()`는 WHERE에 못 쓴다)라 OFFSET을 쓴다.
        val sql = """
            SELECT t.catalog_id, t.title, t.address, t.map_x, t.map_y,
                   ST_Distance(t.location, $CENTER) AS distance_meters,
                   pdb.score(t.id) / (1 + ST_Distance(t.location, $CENTER) / $DISTANCE_HALF_LIFE_M) AS relevance
            FROM spot_catalog_translation t
            WHERE $where
            ORDER BY relevance DESC, t.catalog_id ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val rows = jdbcTemplate.query(sql, { rs, _ ->
            SearchRow(
                catalogId = rs.getLong("catalog_id"),
                title = rs.getString("title"),
                address = rs.getString("address"),
                mapX = rs.getDouble("map_x"),
                mapY = rs.getDouble("map_y"),
                distanceMeters = rs.getDouble("distance_meters"),
            )
        }, *params.toTypedArray())

        val cards = loadCards(rows.map { it.catalogId })
        // 카드를 못 찾은 행은 뺀다. 1단계 정렬 순서는 유지된다.
        return rows.mapNotNull { row ->
            val card = cards[row.catalogId] ?: return@mapNotNull null
            SpotView(
                // 클라이언트에 나가는 id는 카탈로그 id다(SPEC-0019).
                id = row.catalogId,
                title = row.title,
                address = row.address,
                thumbnailImageUrl = card.thumbnailImageUrl.orEmpty(),
                mapX = row.mapX,
                mapY = row.mapY,
                category = card.category,
                copyright = card.copyright,
                distanceMeters = row.distanceMeters,
            )
        }
    }

    /**
     * 1단계에서 뽑힌 카탈로그의 카드 값(썸네일·분류·저작권)을 한 번의 `IN` 조회로 읽는다.
     * - 카테고리가 없는 카탈로그는 결과에서 빠진다.
     */
    private fun loadCards(catalogIds: List<Long>): Map<Long, SpotCard> {
        if (catalogIds.isEmpty()) return emptyMap()

        val placeholders = catalogIds.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT id, thumbnail_image_url, category, copyright
            FROM spot_catalog cat
            WHERE cat.id IN ($placeholders)
            """.trimIndent(),
            { rs, _ ->
                rs.getLong("id") to SpotCard(
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory(rs.getString("category")),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                )
            },
            *catalogIds.toTypedArray(),
        ).toMap()
    }

    /** 1단계(BM25 평면 질의)가 돌려주는 행. 카드 값이 아직 붙기 전이다. */
    private data class SearchRow(
        val catalogId: Long,
        val title: String,
        val address: String?,
        val mapX: Double,
        val mapY: Double,
        val distanceMeters: Double,
    )

    /** 2단계가 채우는 카탈로그의 카드 값. */
    private data class SpotCard(
        val thumbnailImageUrl: String?,
        val category: SpotCategory,
        val copyright: CopyrightType,
    )

    /** [search]와 같은 조건에 걸리는 전체 건수. 검색 페이지 표시와 hasNext 판정에 쓰인다. */
    override fun count(query: SpotSearchQuery): Int {
        val strict = runCount(query, fuzzyOnly = false)
        // [search]와 같은 폴백 규칙이어야 목록과 건수가 어긋나지 않는다.
        if (strict > 0) return strict
        return runCount(query, fuzzyOnly = true)
    }

    private fun runCount(query: SpotSearchQuery, fuzzyOnly: Boolean): Int {
        val (where, params) = whereClause(query, fuzzyOnly)
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spot_catalog_translation t WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /** 제목 접두 자동완성(R9). 오타는 허용하지 않고, 제목·id 오름차순이다. */
    override fun suggest(
        prefix: String,
        language: DomainLocale,
        center: Coordinates,
        radiusMeters: Int,
        limit: Int,
    ): List<SpotSuggestionView> {
        // 조인을 붙이지 않는다(클래스 주석 참조). 분류는 [loadCards]가 채운다.
        val rows = jdbcTemplate.query(
            """
            SELECT t.catalog_id, t.title
            FROM spot_catalog_translation t
            WHERE t.visible
              AND t.language = ?
              AND ${titleField(language)} @@@ ?::pdb.fuzzy(0, t)
              AND ST_DWithin(t.location, $CENTER, ?)
            ORDER BY t.title ASC, t.catalog_id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("catalog_id") to rs.getString("title") },
            language.name,
            toPhrase(prefix),
            center.longitude,
            center.latitude,
            radiusMeters.toDouble(),
            limit,
        )

        val cards = loadCards(rows.map { it.first })
        return rows.mapNotNull { (catalogId, title) ->
            val card = cards[catalogId] ?: return@mapNotNull null
            SpotSuggestionView(
                // 제안이 돌려주는 id도 카탈로그 id다(SPEC-0019).
                id = catalogId,
                title = title,
                category = card.category,
            )
        }
    }

    /**
     * 조회와 카운트가 함께 쓰는 조건과, `?` 등장 순서대로 담은 파라미터.
     * - 반경 필터는 `ST_DWithin`이어야 한다 — `ST_Distance(...) <= r`은 GiST 인덱스를 못 탄다.
     */
    private fun whereClause(query: SpotSearchQuery, fuzzyOnly: Boolean = false): Pair<String, List<Any>> {
        val titleField = titleField(query.language)
        val conditions = mutableListOf(
            // 원본이 사라진 카탈로그를 감춘다. 2단계에서 거르면 건수와 목록이 어긋난다.
            "t.visible",
            // 언어 격리는 이 조건이 전담한다(AC-2.3).
            "t.language = ?",
            matchClause(titleField, query.text, fuzzyOnly),
            // 좌표가 없는 번역 행은 이 조건에서 빠진다.
            "ST_DWithin(t.location, $CENTER, ?)",
        )
        // 원문이 아니라 [toPhrase]로 감싼 값을 넘긴다.
        val phrase = toPhrase(query.text)
        val params = mutableListOf<Any>(query.language.name, phrase)
        // 정확 모드는 제목·주소 두 자리, 폴백 모드는 제목 fuzzy 한 자리다.
        if (!fuzzyOnly) params += phrase
        params.addAll(
            listOf(
                query.center.longitude,
                query.center.latitude, // ST_DWithin의 CENTER
                query.radiusMeters.toDouble(),
            ),
        )

        // 번역 행의 분류 사본으로 거른다. 다중 선택은 OR(SPEC-0020), 비어 있으면 조건을 걸지 않는다.
        if (!query.categories.isUnfiltered) {
            val codes = query.categories.codes()
            conditions += "t.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }

        return conditions.joinToString(" AND ") to params
    }

    /**
     * 매칭 절(R3). 기본은 제목 정확 일치 + 주소, [fuzzyOnly]면 제목 오타 허용만 본다.
     * - 오타 허용을 정확 일치와 OR로 함께 걸면 순위가 오염된다 — 정확 일치 0건일 때만 폴백한다.
     */
    private fun matchClause(titleField: String, text: String, fuzzyOnly: Boolean): String = if (fuzzyOnly) {
        "$titleField @@@ ?::pdb.fuzzy(1)"
    } else {
        "($titleField @@@ ?::pdb.boost(3)" +
            " OR $ADDRESS_FIELD @@@ ?::pdb.boost($ADDRESS_WEIGHT))"
    }

    /** 질의 언어의 제목 필드 식(AC-2.2). 한국어는 별칭 없는 기본 필드다(search-node V4). */
    private fun titleField(language: DomainLocale): String = when (language) {
        DomainLocale.KO -> "t.title"
        DomainLocale.JA -> "t.title::pdb.alias('title_ja')"
        DomainLocale.ZH -> "t.title::pdb.alias('title_zh')"
        DomainLocale.EN -> "t.title::pdb.alias('title_en')"
    }

    companion object {
        /**
         * 사용자 입력을 큰따옴표 구문(phrase)으로 감싼다.
         * - `(`·`:` 같은 문자가 pg_search 구문으로 해석돼 질의가 깨지는 것을 막는다.
         * - 입력의 큰따옴표는 공백으로 바꾼다.
         */
        internal fun toPhrase(text: String): String = "\"" + text.replace("\"", " ") + "\""

        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

        const val MIN_FUZZY_LENGTH = 3

        /** 거리 감쇠의 반감 거리(미터). 관련도는 `BM25 / (1 + 거리 / 이 값)`이다. */
        const val DISTANCE_HALF_LIFE_M = "5000.0"

        /** 주소 매칭 가중치. 제목(3)보다 훨씬 낮아야 주소가 우연히 겹친 spot이 제목 일치를 이기지 못한다. */
        const val ADDRESS_WEIGHT = "0.2"

        /** 주소 필드(AC-1.4). n-gram 색인이라 언어와 무관하다. */
        const val ADDRESS_FIELD = "coalesce(t.address, '')"
    }
}
