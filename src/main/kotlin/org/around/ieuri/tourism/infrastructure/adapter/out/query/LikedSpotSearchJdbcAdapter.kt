package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.port.out.query.LikedSpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikedSpotCursor
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * 좋아요 목록 내 검색 어댑터(SPEC-0033). 생 SQL인 이유는 pg_search 연산자(`@@@`)를 QueryDSL로
 * 표현할 수 없어서다.
 *
 * **메인 DB가 아니라 검색 노드를 본다**(searchJdbcTemplate). `spot_reaction`·`spot_catalog`도
 * 논리 복제로 그쪽에 있어(실측: `pg_subscription_rel` 6개 테이블 전부 `r`) 조인이 성립한다.
 *
 * **`spot_reaction`을 조인한다 — `searchSpots`와 다른 점이다.** 그 어댑터의 주석은 "어떤 조인도
 * 붙일 수 없다"고 적고 있으나 pg_search 0.25.1에서 실측하면 조인은 정상 동작하며 BM25 인덱스도
 * 탄다. 다만 조인이 붙으면 실행 모드가 `TopKScanExecState`에서 `NormalScanExecState`로 바뀌어
 * **`LIMIT`으로 조기 종료하지 못하고 매칭 집합 전체를 읽는다**(실측: "서울" 6,764행). 좋아요
 * 조건이 BM25 인덱스 밖에 있어 pg_search가 상위 N개만으로 답을 확정할 수 없기 때문이다.
 * 관련도 정렬로 바꿔도 마찬가지라, 원인은 정렬 키가 아니라 조인 자체다.
 *
 * 그 비용을 감수하는 이유는 **오타·형태소**다. 단순 부분 일치로는 "경북궁"이 0건이 되는데,
 * 자기가 저장해 둔 것을 찾는 화면에서 그것은 성능 이전에 기능이 성립하지 않는 것이다.
 *
 * **방금 누른 좋아요가 잠시 검색에 안 잡힐 수 있다.** 복제가 도달하기 전이면 검색어 없는
 * 목록(메인 DB)에는 있는 항목이 검색 결과(복제본)에는 없다 — 같은 화면에서 검색어를 넣고
 * 빼는 것만으로 항목이 사라졌다 나타난다. 복제 지연이 통상 1초 미만이라 감수한 위험이며,
 * 이를 없애려면 검색어 없는 조회까지 복제본으로 옮겨야 해 목록 전체가 지연을 안게 된다.
 */
@Component
class LikedSpotSearchJdbcAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : LikedSpotSearchPort {

    /**
     * 좋아요 중 [text]에 일치하는 한 페이지. 정렬은 `likedAt DESC, catalogId DESC` 고정이며
     * **BM25 점수를 계산하지도 정렬에 쓰지도 않는다**(SPEC-0033 R3) — 순수 필터로만 쓴다.
     *
     * 정확 일치로 먼저 찾고 0건일 때만 오타 검색으로 폴백한다([matchClause] 참조).
     *
     * **페이지마다 같은 규칙을 적용한다.** `searchSpots`는 첫 페이지가 아니면 폴백하지 않지만
     * 그쪽은 `count()`가 같은 폴백을 다시 적용해 총계를 맞춰 주기 때문이고, 이 경로에는 그런
     * 두 번째 질의가 없다. 여기서 `after != null`을 근거로 폴백을 막으면 **오타 질의가 항상
     * 1페이지에서 끊긴다** — 2페이지의 정확 일치가 0건이라 빈 페이지가 나가고 `hasNextPage`가
     * false가 된다(AC4.2·AC4.3 위반).
     *
     * 정렬이 `likedAt DESC` 고정이고 BM25 점수를 쓰지 않으므로, 두 모드는 **순서가 아니라
     * 포함 여부만** 다르다 — 페이지마다 판정해도 순서가 흔들리지 않는다.
     */
    @Transactional(readOnly = true)
    override fun searchLikedSpots(query: LikedSpotsSearch, text: String): CursorPage<LikedSpotView> {
        val strict = runSearch(query, text, fuzzyOnly = false)
        if (strict.isNotEmpty()) return page(query, strict)
        return page(query, runSearch(query, text, fuzzyOnly = true))
    }

    private fun runSearch(query: LikedSpotsSearch, text: String, fuzzyOnly: Boolean): List<LikedSpotView> {
        val cursor = query.after?.let {
            try {
                LikedSpotCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val conditions = mutableListOf(
            "r.member_id = ?",
            "r.reaction = 'LIKE'",
            "r.language = ?",
            "t.language = ?",
            matchClause(query.language, text, fuzzyOnly),
        )
        val params = mutableListOf<Any>(
            query.memberId,
            query.language.name,
            query.language.name,
        )
        params += toPhrase(text)
        // 정확 모드는 제목·주소 두 자리, 폴백 모드는 제목 fuzzy 한 자리다([matchClause] 참조).
        if (!fuzzyOnly) params += toPhrase(text)

        if (!query.categories.isUnfiltered) {
            // **번역 행의 복사본(`t.category`)으로 거른다** — `searchSpots`가 거르는 것과 같은
            // 출처다(V55가 이 용도로 복사했다). `spot_catalog`를 보면 두 검색이 같은 화면에서
            // 서로 다른 출처로 같은 필터를 판정하게 된다.
            val codes = query.categories.codes()
            conditions += "t.category IN (${codes.joinToString(",") { "?" }})"
            params.addAll(codes)
        }

        // 정렬이 (updated_at DESC, catalog_id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
        cursor?.let {
            conditions += "(r.updated_at < ? OR (r.updated_at = ? AND r.catalog_id < ?))"
            params += Timestamp.from(it.updatedAt)
            params += Timestamp.from(it.updatedAt)
            params += it.catalogId
        }

        params += query.first + 1

        // **카드 값을 조인 한 번으로 함께 읽는다.** `searchSpots`가 2단계로 읽는 것은 그쪽이
        // 조인을 못 붙이기 때문이고, 이 경로는 조인이 되므로 그 이유가 없다. 나눠 읽으면
        // 왕복이 하나 늘 뿐 아니라, 2단계에서 행이 빠질 때 `hasNextPage` 판정이 틀어진다.
        // `spot_catalog`의 세 컬럼은 NOT NULL이라 조인이 성립하면 값이 반드시 있다.
        //
        // **`visible`을 걸지 않는다.** 원본이 사라진 장소라도 사용자가 누른 좋아요는 보여야
        // 한다(사용자 결정 2026-08-15) — 메인 DB의 좋아요 목록 조회와 같은 규칙이다.
        val sql = """
            SELECT t.catalog_id, t.title, t.address, r.updated_at,
                   c.thumbnail_image_url, c.category, c.copyright
            FROM spot_catalog_translation t
            JOIN spot_reaction r ON r.catalog_id = t.catalog_id
            JOIN spot_catalog c ON c.id = t.catalog_id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY r.updated_at DESC, r.catalog_id DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val copyright = CopyrightType.valueOf(rs.getString("copyright"))
            val catalogId = rs.getLong("catalog_id")
            LikedSpotView(
                // **클라이언트에 나가는 것은 카탈로그 id다**(SPEC-0019).
                id = catalogId,
                title = rs.getString("title"),
                thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                address = rs.getString("address"),
                category = SpotCategory(rs.getString("category")),
                copyright = copyright,
                // copyright에서 도출되는 값이라 적재하지 않는다(둘이 어긋날 여지를 없앤다).
                attributionText = copyright.attributionText,
                likedAt = rs.getTimestamp("updated_at").toInstant(),
                catalogId = catalogId,
            )
        }, *params.toTypedArray())
    }

    /** 한 건 더 읽어 다음 페이지 유무를 판정하고 커서를 붙인다. 메인 DB 경로와 같은 규약이다. */
    private fun page(query: LikedSpotsSearch, rows: List<LikedSpotView>): CursorPage<LikedSpotView> {
        val hasNextPage = rows.size > query.first
        val pageRows = if (hasNextPage) rows.take(query.first) else rows
        val edges = pageRows.map {
            CursorEdge(cursor = LikedSpotCursor(it.likedAt, it.catalogId).encode(), node = it)
        }

        return CursorPage(
            edges = edges,
            pageInfo = CursorPageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = query.after != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor,
            ),
            // 좋아요 목록은 총 개수를 노출하지 않는다 — 소진 판정은 hasNextPage로 충분하다.
            totalCount = null,
        )
    }

    /**
     * 제목·주소 매칭 절. 정확 모드는 제목과 주소를 OR로 함께 보고, 폴백 모드는 제목만 오타
     * 허용으로 본다.
     *
     * **가중치(boost)를 쓰지 않는다** — `searchSpots`는 순위를 다투므로 제목 3·주소 0.2로
     * 눌러야 하지만, 이쪽은 정렬이 `likedAt DESC` 고정이라 점수가 순서에 관여하지 않는다.
     * 포함 여부만 판정하면 된다.
     */
    private fun matchClause(language: DomainLocale, text: String, fuzzyOnly: Boolean): String =
        if (fuzzyOnly) {
            "${titleField(language)} @@@ ?::pdb.fuzzy(1)"
        } else {
            "(${titleField(language)} @@@ ? OR $ADDRESS_FIELD @@@ ?)"
        }

    /**
     * 질의 언어에 해당하는 제목 필드 식. BM25 인덱스는 테이블당 하나뿐이라 언어별로 나눌 수
     * 없고, 하나의 인덱스 안에 언어별 토크나이저 필드가 별칭으로 들어 있다(search-node V4).
     * **한국어가 별칭 없는 기본 필드다.**
     */
    private fun titleField(language: DomainLocale): String = when (language) {
        DomainLocale.KO -> "t.title"
        DomainLocale.JA -> "t.title::pdb.alias('title_ja')"
        DomainLocale.ZH -> "t.title::pdb.alias('title_zh')"
        DomainLocale.EN -> "t.title::pdb.alias('title_en')"
    }

    companion object {
        /**
         * 주소 매칭 필드. 원본이 null일 수 있어 빈 문자열로 대체해 색인돼 있다(search-node V4).
         *
         * **색인 표현식을 그대로 적는다.** `SpotSearchJdbcAdapter`의 같은 이름 상수는 캐스트를
         * 생략하고 pg_search의 추론에 맡기는데, 둘 다 동작하지만 **같은 이름의 다른 문자열**이라
         * 서로 같다고 넘겨짚지 않도록 여기 적어 둔다.
         */
        const val ADDRESS_FIELD = "(COALESCE(t.address, ''))::pdb.ngram('2','3')"

        /**
         * 사용자 입력을 pg_search 질의 문자열로 안전하게 감싼다. `@@@`의 오른쪽은 값이 아니라
         * **질의 언어**라, 바인딩해도 `(`·`:`·`~` 같은 문자가 구문으로 해석돼 질의가 깨진다.
         * 큰따옴표로 감싸면 그 안에서 전부 리터럴이 된다.
         *
         * 입력에 든 큰따옴표는 제거한다 — 감싸는 따옴표와 충돌해 파서를 깨뜨린다.
         */
        internal fun toPhrase(text: String): String = "\"" + text.replace("\"", " ") + "\""
    }
}
