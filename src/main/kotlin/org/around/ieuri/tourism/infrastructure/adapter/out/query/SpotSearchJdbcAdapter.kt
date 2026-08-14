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
 * 인덱스는 테이블당 하나이고 언어를 가르지 않는다(search-node V4). **언어 격리는 조회의
 * `language = ?` 조건이 전담한다** — 빠뜨리면 다른 언어 행이 결과에 섞인다(AC-2.3). 다만
 * SPEC-0019 이후 그 조건의 의미가 "그 언어의 spot 행이 있는가"에서 **"그 언어의 제목이
 * 있는가"**로 바뀌었다. 번역된 카탈로그가 검색에 걸리는 것이 그 변경의 목적이다.
 *
 * **검색은 `spot_catalog_translation` 단독으로 돈다**(SPEC-0019). BM25 인덱스가 그 테이블에
 * 걸리고, pg_search는 `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 전부 같은 질의 레벨에 있어야
 * 인덱스로 밀어넣기 때문에 **어떤 조인도 붙일 수 없다.** 거리 필터·정렬에 필요한 좌표는 그래서
 * 번역 행에 복사돼 있다(V52).
 *
 * 카드에 필요한 나머지 값(썸네일·분류·저작권)은 번역 테이블에 없어 **[loadCards]가 2단계로
 * 읽는다.** 거리 필터가 1단계에서 이미 걸렸으므로 2단계는 이미 좁혀진 한 페이지에 대한 단순
 * 조회이며, "전국 상위 N을 뽑고 나중에 거른다"는 문제가 생기지 않는다.
 *
 * 검색과 카운트를 한 클래스에 두어 조건을 눈으로 대조할 수 있게 한다 — 갈리면 목록과 건수가 어긋난다.
 */
@Component
class SpotSearchJdbcAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : SpotSearchPort {

    /** 관련도(거리 가중) 내림차순 + id 오름차순 한 페이지. 다음 페이지 판정은 호출자가 [count]로 한다. */
    @Transactional(readOnly = true)
    override fun search(query: SpotSearchQuery): List<SpotSearchHit> {
        val strict = runSearch(query, fuzzyOnly = false)
        // **정확 일치가 하나도 없을 때만 오타 검색으로 폴백한다**([matchClause] 참조).
        // 첫 페이지에서 0건이면 그 질의는 정확 모드로 결과가 없다는 뜻이다 — 뒤 페이지를
        // 보고 있다면 앞 페이지에 결과가 있었으므로 폴백하지 않는다.
        if (strict.isNotEmpty() || query.offset > 0) return strict
        return runSearch(query, fuzzyOnly = true)
    }

    private fun runSearch(query: SpotSearchQuery, fuzzyOnly: Boolean): List<SpotSearchHit> {
        val (where, whereParams) = whereClause(query, fuzzyOnly)
        // **파라미터 순서는 `?` 등장 순서다.** SELECT의 거리·관련도가 CENTER를 하나씩 쓰고
        // 각각 좌표 둘을 소비하므로, WHERE 몫보다 먼저 넣는다.
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude, // ST_Distance의 CENTER
            query.center.longitude, query.center.latitude, // relevance 감쇠항의 CENTER
        )
        params.addAll(whereParams)

        params += query.limit
        params += query.offset

        // **평면 쿼리여야 한다.** `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 **모두 같은 레벨**에
        // 있어야 pg_search가 인덱스로 밀어넣는다. 서브쿼리·CTE·조인으로 감싸면 그 조건이 깨져
        // `query is incompatible with @@@ operator`로 실패한다(실측).
        //
        // **그래서 `spot_catalog`를 조인하지 않는다.** 거리에 필요한 좌표는 번역 행에 복사돼
        // 있고(V52), 카드의 나머지 값은 [loadCards]가 2단계로 읽는다.
        //
        // 커서 seek도 같은 이유로 쓸 수 없다 — 조건절에 관련도를 넣으려면 `pdb.score()`를
        // WHERE에 써야 하는데 그건 SELECT 전용 자리표시자라 `Unsupported query shape`가 된다.
        // OFFSET은 반대로 인덱스 안으로 밀려 들어가 top-(limit+offset) 힙으로 처리된다.
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
                relevance = rs.getDouble("relevance"),
            )
        }, *params.toTypedArray())

        val cards = loadCards(rows.map { it.catalogId })
        // 카드를 못 찾은 행은 뺀다 — 카테고리가 없으면 도메인 enum으로 옮길 값이 없다.
        // 1단계 정렬 순서는 그대로 유지된다.
        return rows.mapNotNull { row ->
            val card = cards[row.catalogId] ?: return@mapNotNull null
            SpotSearchHit(
                spot = SpotSearchResult(
                    // **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019).
                    spotId = row.catalogId,
                    title = row.title,
                    address = row.address,
                    thumbnailImageUrl = card.thumbnailImageUrl.orEmpty(),
                    mapX = row.mapX,
                    mapY = row.mapY,
                    category = card.category,
                    copyright = card.copyright,
                    distanceMeters = row.distanceMeters,
                ),
                relevance = row.relevance,
            )
        }
    }

    /**
     * 1단계에서 뽑힌 카탈로그의 카드 값. 썸네일·분류·저작권은 번역 테이블에 없어 여기서 읽는다.
     *
     * **한 페이지분(수십 건)에 대한 `IN` 조회 한 번이다.** 거리 필터가 1단계에서 이미 걸려
     * 집합이 좁으므로, "전국 상위 N을 뽑고 나중에 거른다"는 문제가 생기지 않는다.
     *
     * 카테고리가 없는 카탈로그는 결과에서 뺀다 — 도메인 enum에 미분류 값이 없어 임의의 기본값을
     * 만들면 잘못된 분류로 노출된다.
     */
    private fun loadCards(catalogIds: List<Long>): Map<Long, SpotCard> {
        if (catalogIds.isEmpty()) return emptyMap()

        val placeholders = catalogIds.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT id, thumbnail_image_url, category, copyright
            FROM spot_catalog cat
            WHERE cat.id IN ($placeholders)
              AND ${LiveCatalogPredicate.sql("cat")}
            """.trimIndent(),
            { rs, _ ->
                rs.getLong("id") to SpotCard(
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    category = SpotCategory.valueOf(rs.getString("category")),
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
        val relevance: Double,
    )

    /** 2단계가 채우는 카탈로그의 카드 값. */
    private data class SpotCard(
        val thumbnailImageUrl: String?,
        val category: SpotCategory,
        val copyright: CopyrightType,
    )

    /** [search]와 같은 조건에 걸리는 전체 건수. 검색 페이지 표시와 hasNext 판정에 쓰인다. */
    @Transactional(readOnly = true)
    override fun count(query: SpotSearchQuery): Int {
        val strict = runCount(query, fuzzyOnly = false)
        // [search]와 **같은 폴백 규칙**을 써야 목록과 건수가 어긋나지 않는다.
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
    ): List<SpotSuggestionHit> {
        // **어떤 조인도 붙이지 않는다** — 조인하면 pg_search가 인덱스를 못 타고 `@@@`가 깨진다.
        // 그래서 분류는 여기서 못 읽고 [loadCards]가 2단계로 채운다.
        val rows = jdbcTemplate.query(
            """
            SELECT t.catalog_id, t.title
            FROM spot_catalog_translation t
            WHERE t.language = ?
              AND ${titleField(language)} @@@ ?::pdb.fuzzy(0, t)
              AND ST_DWithin(t.location, $CENTER, ?)
            ORDER BY t.title ASC, t.catalog_id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("catalog_id") to rs.getString("title") },
            language.name, toPhrase(prefix),
            center.longitude, center.latitude, radiusMeters.toDouble(),
            limit,
        )

        val cards = loadCards(rows.map { it.first })
        return rows.mapNotNull { (catalogId, title) ->
            val card = cards[catalogId] ?: return@mapNotNull null
            SpotSuggestionHit(
                // **제안이 돌려주는 id도 카탈로그 id다**(SPEC-0019).
                spotId = catalogId,
                title = title,
                category = card.category,
            )
        }
    }

    /**
     * 조회와 카운트가 **같은 조건**을 쓰도록 한 곳에서 만든다 — 갈리면 목록과 건수가 어긋난다.
     * 파라미터는 SQL의 `?` 등장 순서대로 담아 함께 반환한다.
     *
     * 반경 필터는 반드시 `ST_DWithin`이다. `ST_Distance(...) <= r`로 쓰면 전 행을 계산해
     * GiST 인덱스를 타지 못한다.
     */
    private fun whereClause(query: SpotSearchQuery, fuzzyOnly: Boolean = false): Pair<String, List<Any>> {
        val titleField = titleField(query.language)
        val conditions = mutableListOf(
            // **언어 격리는 이 조건이 전담한다.** BM25 인덱스가 하나뿐이라 언어를 가르지
            // 못하므로, 빠뜨리면 다른 언어 행이 결과에 섞인다(AC-2.3). SPEC-0019 이후 이 조건은
            // "그 언어의 제목이 있는가"를 뜻한다 — 그 언어의 spot 행 유무가 아니다.
            "t.language = ?",
            // **제목은 정확 일치와 오타 허용을 OR로 함께 건다.**
            //
            // `pdb.fuzzy` 단독으로 쓰면 BM25 점수가 상수로 고정된다(실측: 전부 1.5) — 그러면
            // 관련도 순위가 사라져 사실상 거리순 정렬만 남고 R1이 깨진다. 정확 일치 절을 함께
            // 걸면 점수가 되살아난다(실측: "경복궁" 13.6 → 11.7 차등, "경복궁"이 1등).
            //
            // 오타 질의는 정확 일치가 0건이라 fuzzy 쪽만 남아 점수가 평탄해지는데, 그것은
            // 자연스럽다 — 오타를 친 시점에는 무엇이 더 관련 있는지 판단할 근거가 없다.
            //
            // **짧은 질의(2글자 이하)는 주소 절을 빼고 제목만 본다**(아래 [matchClause]).
            //
            // **제목은 올리고(3) 주소는 내린다(0.2).** 두 필드의 점수 스케일이 크게 다르기
            // 때문이다 — n-gram은 긴 질의를 잘게 쪼개 각 조각을 매칭시키므로 점수가 폭증한다.
            //
            // 실측한 실패: "BCBG 현대프리미엄아울렛 김포점"을 그대로 검색했을 때 제목이 완전히
            // 일치하는 spot(196.8)보다, 주소가 "…현대프리미엄아울렛김포점3층"인 **올리브영**이
            // 308.6으로 이겨 1위를 차지했다. 제목이 정확히 맞는데 주소가 우연히 겹친 쪽에
            // 밀리는 것은 사용자가 납득할 수 없는 순서다.
            //
            // 주소를 0.2로 낮춰도 주소 검색은 그대로 된다(실측: "두산로" 3건 정상). 주소는
            // 제목에 없는 것을 찾는 **보조 경로**이지 순위를 뒤집을 근거가 아니다.
            matchClause(titleField, query.text, fuzzyOnly),
            // **CTE와 조인하지 않는다** — `t, c` 형태면 pg_search가 인덱스를 못 타고
            // `@@@`가 깨진다. 좌표를 두 번 더 바인딩하더라도 평면 조건을 유지한다.
            //
            // 좌표가 없는 번역 행은 이 조건에서 자연히 빠진다(상류 결손 317건).
            "ST_DWithin(t.location, $CENTER, ?)",
        )
        // **원문이 아니라 [toPhrase]로 감싼 값을 넘긴다** — 괄호·콜론 같은 문자가
        // pg_search 구문으로 해석돼 질의가 깨지는 것을 막는다.
        val phrase = toPhrase(query.text)
        val params = mutableListOf<Any>(query.language.name, phrase)
        // 정확 모드는 제목·주소 두 자리, 폴백 모드는 제목 fuzzy 한 자리다([matchClause] 참조).
        // 짧은 질의는 주소 절이 빠지므로 그때만 파라미터를 뺀다 — [matchClause] 참조.
        if (!fuzzyOnly) params += phrase
        params.addAll(
            listOf(
                query.center.longitude, query.center.latitude, // ST_DWithin의 CENTER
                query.radiusMeters.toDouble(),
            ),
        )

        // **카테고리 필터를 걸지 않는다** — 분류는 `spot_catalog`에 있고, 그것을 조인하는 순간
        // 평면 질의가 깨져 BM25 인덱스 푸시다운이 사라진다. 2단계([loadCards])에서 거르는 것도
        // 답이 아니다: 한 페이지를 먼저 자른 뒤 거르면 요청한 size보다 적게 나가고 건수도
        // 어긋난다. 이 제약을 어떻게 풀지는 스펙 결정 사항이라 여기서 임의로 메우지 않는다.
        return conditions.joinToString(" AND ") to params
    }

    /**
     * 매칭 절. **정확 모드와 폴백 모드가 다르다**(R3).
     *
     * 기본은 정확 모드다 — 제목 정확 일치 + 주소. [fuzzyOnly]면 제목 오타 허용만 본다.
     *
     * **오타 허용을 정확 일치와 OR로 함께 걸지 않는다.** `pdb.fuzzy`의 BM25 점수는 사실상
     * 평탄해서(실측: boost를 0.1·0.01로 낮춰도 순위가 그대로) 함께 걸면 오타 매칭이 순위를
     * 오염시킨다 — `롯데`를 정확히 쳤는데 1위가 바뀌고 결과 수가 1018→996으로 줄었으며,
     * `가산`은 `팔도명가산야채순대국`이 1위로 올라왔다.
     *
     * 그래서 **정확 일치가 0건일 때만** 폴백한다([SpotSearchJdbcAdapter.search]·[count]).
     * 그러면 정타 질의의 순위는 전혀 건드리지 않으면서, `롣데`처럼 정확 일치가 없는 오타에서만
     * 오타 검색이 동작한다(실측: 995건, 상위가 전부 롯데 계열).
     *
     * 폴백에서 주소를 빼는 이유: 주소는 n-gram이라 2글자 조각이 아무 데나 걸려
     * `강남역데이원의원` 같은 무관한 항목이 1위로 올라온다(실측).
     */
    private fun matchClause(titleField: String, text: String, fuzzyOnly: Boolean): String =
        if (fuzzyOnly) {
            "$titleField @@@ ?::pdb.fuzzy(1)"
        } else {
            "($titleField @@@ ?::pdb.boost(3)" +
                " OR $ADDRESS_FIELD @@@ ?::pdb.boost($ADDRESS_WEIGHT))"
        }


    /**
     * 질의 언어에 해당하는 제목 필드 식(AC-2.2).
     *
     * BM25 인덱스는 테이블당 하나뿐이라(pg_search 제약) 언어별로 나눌 수 없고, 대신 하나의
     * 인덱스 안에 언어별 토크나이저 필드가 별칭으로 들어 있다(search-node V4).
     * **한국어가 별칭 없는 기본 필드다** — 코퍼스에서 가장 큰 언어라 흔한 경로를 짧게 뒀다.
     */
    private fun titleField(language: DomainLocale): String = when (language) {
        DomainLocale.KO -> "t.title"
        DomainLocale.JA -> "t.title::pdb.alias('title_ja')"
        DomainLocale.ZH -> "t.title::pdb.alias('title_zh')"
        DomainLocale.EN -> "t.title::pdb.alias('title_en')"
    }


    companion object {
        /**
         * 사용자 입력을 pg_search 질의 문자열로 안전하게 감싼다.
         *
         * **`@@@`의 오른쪽은 값이 아니라 질의 언어다.** 바인딩 파라미터로 넘겨도 문자열 내용이
         * 파서에 그대로 들어가므로, `(`·`)`·`:`·`[`·`]`·`{`·`}`·`^`·`~` 같은 문자가 구문으로
         * 해석돼 질의가 통째로 깨진다(SQL 인젝션은 아니지만 사용자가 서버 오류를 유발한다):
         *
         *   "(주)뮬라프렌즈 가로수길점" →
         *   ERROR: could not parse query string 'title:((주)뮬라프렌즈 가로수길점)'
         *
         * **큰따옴표로 감싸 구문(phrase)으로 만든다.** 그 안에서는 특수문자가 전부 리터럴이 되어
         * 어떤 입력이 와도 파서가 깨지지 않는다. 문자를 골라 백슬래시로 이스케이프하는 방법도
         * 있으나, 특수문자 목록을 우리가 관리해야 하고 하나라도 빠뜨리면 같은 사고가 난다.
         *
         * 정상 질의의 결과는 달라지지 않는다(실측: 롯데 12,227건·경복궁 42건으로 동일).
         *
         * 입력에 든 큰따옴표는 **제거한다** — 감싸는 따옴표와 충돌해 파서를 깨뜨리는데,
         * 장소명에서 따옴표는 검색어로서 의미가 없다.
         */
        internal fun toPhrase(text: String): String = "\"" + text.replace("\"", " ") + "\""

        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"

        /** 이 길이 미만의 질의는 주소 절을 빼고 제목만 본다 — [matchClause] 참조. */
        const val MIN_FUZZY_LENGTH = 3

        /**
         * 거리 감쇠의 반감 거리(미터). 관련도는 `BM25 / (1 + 거리 / 이 값)`이라, 이 거리에서
         * 계수가 0.5가 된다.
         *
         * **1km로 두었더니 거리가 관련도를 덮어버렸다.** 13km 지점에서 계수가 0.07까지
         * 떨어져 BM25 차이가 무의미해졌고, "BCBG 현대프리미엄아울렛 김포점"을 그대로
         * 검색해도 BM25가 3배 낮은 "BCBG NC 신구로점"이 가깝다는 이유만으로 1위를 가져갔다
         * (실측: 196.8/13.3km → 13.78 vs 61.5/2.6km → 17.14).
         *
         * 5km면 같은 상황에서 김포점이 1위로 돌아오고(53.8 vs 40.5), 가까운 곳 우선(R6)도
         * 유지된다(실측: "경복궁" 검색에서 0.4km의 "경복궁"이 1위).
         */
        const val DISTANCE_HALF_LIFE_M = "5000.0"

        /**
         * 주소 매칭의 가중치. 제목(3)보다 훨씬 낮다 — 주소는 n-gram이라 긴 질의에서 점수가
         * 폭증해, 그대로 두면 제목이 정확히 일치하는 spot을 주소가 우연히 겹친 spot이 이긴다.
         *
         * **값은 실측으로 정했다.** "BCBG 현대프리미엄아울렛 김포점"을 검색해 제목이 완전히
         * 일치하는 spot이 1위인지로 판정했다:
         *   - 1.0 이상 : 실패(주소에 그 이름이 든 올리브영이 이긴다)
         *   - 0.5      : 성공하지만 98.4 대 97.1로 1%대 차이라 데이터가 조금만 바뀌어도 뒤집힌다
         *   - 0.2~0.4  : 성공하며 결과가 동일. 그중 마진이 가장 큰 하단값을 택했다(98.4 대 74.2)
         *
         * 낮춰도 주소 검색 자체는 죽지 않는다(실측: "두산로" 3건, "금천구" 정상). 가중치는
         * 결과의 유무가 아니라 **다른 필드와의 경쟁**에만 관여하기 때문이다.
         *
         * 주소는 제목에 없는 것을 찾는 보조 경로이지 순위를 뒤집을 근거가 아니다.
         */
        const val ADDRESS_WEIGHT = "0.2"

        /**
         * 주소 필드(AC-1.4). **언어와 무관하다** — 주소는 n-gram으로 색인해 사전을 쓰지 않으므로
         * 제목처럼 언어별 별칭을 고를 필요가 없다.
         *
         * 형태소 분석을 쓰지 않는 이유는 문맥 의존성이다: 주소 안의 "두산로 71"은
         * `{두산, 로, 71}`로 잘리는데 질의 "두산로"는 단독이라 `{두, 산로}`로 잘려 **겹치는
         * 토큰이 사라진다**(실측 0건). n-gram은 그 어긋남이 없다.
         */
        const val ADDRESS_FIELD = "coalesce(t.address, '')"

    }
}
