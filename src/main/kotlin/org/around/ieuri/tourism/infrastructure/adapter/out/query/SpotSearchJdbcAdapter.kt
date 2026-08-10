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
 * 인덱스는 테이블당 하나이고 언어를 가르지 않는다(search-node V2). **언어 격리는 조회의
 * `language = ?` 조건이 전담한다** — 빠뜨리면 다른 언어 행이 결과에 섞인다(AC-2.3).
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
        val (where, whereParams) = whereClause(query)
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
                   pdb.score(s.id) / (1 + ST_Distance(s.location, $CENTER) / $DISTANCE_HALF_LIFE_M) AS relevance
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

    /** [search]와 같은 조건에 걸리는 전체 건수. 검색 페이지 표시와 hasNext 판정에 쓰인다. */
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
            language.name, toPhrase(prefix),
            center.longitude, center.latitude, radiusMeters.toDouble(),
            limit,
        )

    /**
     * 조회와 카운트가 **같은 조건**을 쓰도록 한 곳에서 만든다 — 갈리면 목록과 건수가 어긋난다.
     * 파라미터는 SQL의 `?` 등장 순서대로 담아 함께 반환한다.
     *
     * 반경 필터는 반드시 `ST_DWithin`이다. `ST_Distance(...) <= r`로 쓰면 전 행을 계산해
     * GiST 인덱스를 타지 못한다.
     */
    private fun whereClause(query: SpotSearchQuery): Pair<String, List<Any>> {
        val titleField = titleField(query.language)
        val conditions = mutableListOf(
            // **언어 격리는 이 조건이 전담한다.** BM25 인덱스가 하나뿐이라 언어를 가르지
            // 못하므로, 빠뜨리면 다른 언어 행이 결과에 섞인다(AC-2.3).
            "s.language = ?",
            // **제목은 정확 일치와 오타 허용을 OR로 함께 건다.**
            //
            // `pdb.fuzzy` 단독으로 쓰면 BM25 점수가 상수로 고정된다(실측: 전부 1.5) — 그러면
            // 관련도 순위가 사라져 사실상 거리순 정렬만 남고 R1이 깨진다. 정확 일치 절을 함께
            // 걸면 점수가 되살아난다(실측: "경복궁" 13.6 → 11.7 차등, "경복궁"이 1등).
            //
            // 오타 질의는 정확 일치가 0건이라 fuzzy 쪽만 남아 점수가 평탄해지는데, 그것은
            // 자연스럽다 — 오타를 친 시점에는 무엇이 더 관련 있는지 판단할 근거가 없다.
            //
            // **짧은 질의에는 오타 허용을 끈다**(아래 [fuzzyClause]). 2글자에서 편집 거리 1은
            // 글자 하나가 통째로 바뀌는 것이라 "가산"이 안산·부산까지 끌어온다(실측 41건 → 4,499건).
            //
            // 주소는 n-gram이라 fuzzy만으로도 점수가 갈려(실측 22.7/21.0) 이 처리가 필요 없다.
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
            "($titleField @@@ ?::pdb.boost(3)" +
                " OR $titleField @@@ ?${fuzzyClause(query.text)}" +
                " OR $ADDRESS_FIELD @@@ ?::pdb.boost($ADDRESS_WEIGHT))",
            // **CTE와 조인하지 않는다** — `spot s, c` 형태면 pg_search가 인덱스를 못 타고
            // `@@@`가 깨진다. 좌표를 두 번 더 바인딩하더라도 평면 조건을 유지한다.
            "ST_DWithin(s.location, $CENTER, ?)",
        )
        val params = mutableListOf<Any>(
            query.language.name,
            // 제목 정확 일치, 제목 fuzzy, 주소 fuzzy 순서다(위 조건절의 `?` 등장 순서).
            // **원문이 아니라 [toPhrase]로 감싼 값을 넘긴다** — 괄호·콜론 같은 문자가
            // pg_search 구문으로 해석돼 질의가 깨지는 것을 막는다.
            toPhrase(query.text), toPhrase(query.text), toPhrase(query.text),
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
     * 질의 길이에 맞는 오타 허용 절(R3).
     *
     * **짧은 질의에는 오타를 허용하지 않는다.** 편집 거리 1은 "글자 하나가 바뀌어도 같게 본다"는
     * 뜻인데, 2글자에서는 그것이 단어의 절반이라 전혀 다른 말이 된다 — "가산"이 안산·부산을
     * 끌어와 41건짜리 검색이 4,499건이 됐다(실측). 3글자부터는 안정적이다(경복궁 22건).
     *
     * Elasticsearch의 `fuzziness: AUTO`도 같은 규칙을 쓴다 — 짧은 항은 정확 일치만 본다.
     * 반환값은 SQL 조각이라 캐스트가 없으면 그대로 정확 일치가 된다.
     */
    private fun fuzzyClause(text: String): String =
        if (text.length >= MIN_FUZZY_LENGTH) "::pdb.fuzzy(1)" else ""


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

        /** 이 길이 미만의 질의는 오타를 허용하지 않는다 — [fuzzyClause] 참조. */
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
        const val ADDRESS_FIELD = "coalesce(s.address, '')"

    }
}
