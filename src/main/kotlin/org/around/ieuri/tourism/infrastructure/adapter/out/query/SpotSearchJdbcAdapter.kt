package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.SpotSearchHit
import org.around.ieuri.tourism.application.port.out.query.SpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchQuery
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSuggestionHit
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * BM25 전문 검색 어댑터(SPEC-0013 R1-R9). QueryDSL로 표현할 수 없는 것이 둘이라 생 SQL이다:
 * pg_search 연산자(`@@@`·`pdb.score`)와 PostGIS 함수(`ST_DWithin`·`ST_Distance`).
 *
 * **메인 DB가 아니라 검색 노드를 본다**(searchJdbcTemplate). pg_search 커뮤니티 에디션이
 * 물리 복제를 막아, BM25 인덱스를 논리 복제 구독자로 분리했기 때문이다.
 *
 * 인덱스는 언어별로 나뉘어 있다(search-node V2). 조회가 `language = ?`를 함께 걸어야 그 언어의
 * 부분 인덱스가 선택되고, 동시에 다른 언어 행이 결과에 섞이지 않는다.
 *
 * 검색과 카운트를 한 클래스에 두어 조건을 눈으로 대조할 수 있게 한다 — 갈리면 목록과 건수가 어긋난다.
 */
@Component
class SpotSearchJdbcAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : SpotSearchPort {

    /**
     * 관련도 순 한 페이지. `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     *
     * 정렬 키는 **거리 가중이 끝난 최종 점수**이며 커서에 그대로 실린다. BM25 원점수를 커서에
     * 넣으면 커서가 가리키는 위치와 실제 정렬 순서가 어긋난다.
     *
     * 관련도가 계산식이라 별칭을 WHERE에서 참조할 수 없어 서브쿼리로 한 겹 감싼다.
     */
    @Transactional(readOnly = true)
    override fun search(query: SpotSearchQuery): List<SpotSearchHit> {
        val (where, whereParams) = whereClause(query)
        // **파라미터 순서는 `?` 등장 순서다.** SELECT의 거리·관련도가 CENTER를 하나씩 쓰고
        // 각각 좌표 둘을 소비하므로, WHERE 몫보다 먼저 넣는다.
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude, // ST_Distance의 CENTER
            query.center.longitude, query.center.latitude, // relevance 감쇠항의 CENTER
        )
        params.addAll(whereParams)

        // 요청한 개수보다 한 건 더 읽어 호출자가 다음 페이지 존재를 판정한다.
        params += query.limit + 1
        params += query.offset

        // **평면 쿼리여야 한다.** `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 **모두 같은 레벨**에
        // 있어야 pg_search가 인덱스로 밀어넣는다. 서브쿼리·CTE로 감싸면 그 조건이 깨져
        // `query is incompatible with @@@ operator`로 실패한다(실측).
        //
        // 그래서 커서 seek을 쓸 수 없다 — 조건절에 관련도를 넣으려면 `pdb.score()`를 WHERE에
        // 써야 하는데 그건 SELECT 전용 자리표시자라 `Unsupported query shape`가 된다.
        // OFFSET은 반대로 인덱스 안으로 밀려 들어가 top-(limit+offset) 힙으로 처리된다.
        val sql = """
            SELECT s.id, s.title, s.address, s.thumbnail_image_url, s.map_x, s.map_y,
                   s.category, s.copyright,
                   ST_Distance(s.location, $CENTER) AS distance_meters,
                   pdb.score(s.id) / (1 + ST_Distance(s.location, $CENTER) / 1000.0) AS relevance
            FROM spot s
            WHERE $where
            ORDER BY relevance DESC, s.id ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            SpotSearchHit(
                spot = SpotSearchResult(
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                    distanceMeters = rs.getDouble("distance_meters"),
                ),
                relevance = rs.getDouble("relevance"),
            )
        }, *params.toTypedArray())
    }

    /** [search]와 같은 조건(커서 제외)에 걸리는 전체 건수. 검색 페이지가 결과 수를 표시한다. */
    @Transactional(readOnly = true)
    override fun count(query: SpotSearchQuery): Int {
        val (where, params) = whereClause(query)
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spot s WHERE $where",
            Int::class.java,
            *params.toTypedArray(),
        ) ?: 0
    }

    /**
     * 제목 접두 자동완성(R9). 관련도가 아니라 접두 일치라 BM25 점수를 계산하지 않는다 —
     * `pdb.fuzzy`의 두 번째 인자가 접두 매칭 스위치이고, 편집 거리 0이라 오타는 허용하지 않는다.
     */
    @Transactional(readOnly = true)
    override fun suggest(
        prefix: String,
        language: DomainLocale,
        center: Coordinates,
        radiusMeters: Int,
        limit: Int,
    ): List<SpotSuggestionHit> =
        jdbcTemplate.query(
            // CTE와 조인하지 않는다 — 조인하면 pg_search가 인덱스를 못 타고 `@@@`가 깨진다.
            """
            SELECT s.id, s.title, s.category
            FROM spot s
            WHERE s.language = ?
              AND ${titleField(language)} @@@ ?::pdb.fuzzy(0, t)
              AND ST_DWithin(s.location, $CENTER, ?)
            ORDER BY s.title ASC, s.id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                SpotSuggestionHit(
                    spotId = rs.getLong("id"),
                    title = rs.getString("title"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                )
            },
            language.name, prefix,
            center.longitude, center.latitude, radiusMeters.toDouble(),
            limit,
        )

    /**
     * 조회와 카운트가 **같은 조건**을 쓰도록 한 곳에서 만든다 — 갈리면 목록과 건수가 어긋난다.
     * 파라미터는 SQL의 `?` 등장 순서대로 담아 함께 반환한다. CTE의 center가 항상 앞선다.
     *
     * 반경 필터는 반드시 `ST_DWithin`이다. `ST_Distance(...) <= r`로 쓰면 전 행을 계산해
     * GiST 인덱스를 타지 못한다.
     */
    private fun whereClause(query: SpotSearchQuery): Pair<String, List<Any>> {
        val titleField = titleField(query.language)
        val addressField = addressField(query.language)
        val conditions = mutableListOf(
            // **언어 격리는 이 조건이 전담한다.** BM25 인덱스가 하나뿐이라 언어를 가르지
            // 못하므로, 빠뜨리면 다른 언어 행이 결과에 섞인다(AC-2.3).
            "s.language = ?",
            // 오타 허용(R3): 편집 거리 1까지 맞춘다. pg_search의 상한은 성능상 2다.
            "($titleField @@@ ?::pdb.fuzzy(1) OR $addressField @@@ ?::pdb.fuzzy(1))",
            // **CTE와 조인하지 않는다** — `spot s, c` 형태면 pg_search가 인덱스를 못 타고
            // `@@@`가 깨진다. 좌표를 두 번 더 바인딩하더라도 평면 조건을 유지한다.
            "ST_DWithin(s.location, $CENTER, ?)",
        )
        val params = mutableListOf<Any>(
            query.language.name,
            query.text, query.text,
            query.center.longitude, query.center.latitude, // ST_DWithin의 CENTER
            query.radiusMeters.toDouble(),
        )

        query.category?.let {
            conditions += "s.category = ?"
            params += it.name
        }
        return conditions.joinToString(" AND ") to params
    }

    /**
     * 질의 언어에 해당하는 제목 필드 식(AC-2.2).
     *
     * BM25 인덱스는 테이블당 하나뿐이라(pg_search 제약) 언어별로 나눌 수 없고, 대신 하나의
     * 인덱스 안에 언어별 토크나이저 필드가 별칭으로 들어 있다(search-node V2).
     * **한국어가 별칭 없는 기본 필드다** — 코퍼스에서 가장 큰 언어라 흔한 경로를 짧게 뒀다.
     */
    private fun titleField(language: DomainLocale): String = when (language) {
        DomainLocale.KO -> "s.title"
        DomainLocale.JA -> "s.title::pdb.alias('title_ja')"
        DomainLocale.ZH -> "s.title::pdb.alias('title_zh')"
        DomainLocale.EN -> "s.title::pdb.alias('title_en')"
    }

    /** [titleField]와 같은 규약의 주소 필드. 주소만 일치하는 spot을 찾기 위한 것이다(AC-1.4). */
    private fun addressField(language: DomainLocale): String = when (language) {
        DomainLocale.KO -> "coalesce(s.address, '')"
        DomainLocale.JA -> "coalesce(s.address, '')::pdb.alias('address_ja')"
        DomainLocale.ZH -> "coalesce(s.address, '')::pdb.alias('address_zh')"
        DomainLocale.EN -> "coalesce(s.address, '')::pdb.alias('address_en')"
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

    }
}
