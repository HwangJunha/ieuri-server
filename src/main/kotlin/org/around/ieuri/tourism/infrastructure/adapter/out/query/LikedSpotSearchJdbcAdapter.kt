package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.port.out.query.LikedSpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.CopyrightType
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
 * 매칭은 **제목·주소의 n-gram 부분 일치**다(search-node V2, [matchClause] 참조). 형태소
 * 분석기는 재현율을 더 주지 못하면서 37배 느려 쓰지 않는다 — 근거는 [matchClause]에 있다.
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
     * **질의는 한 번이다**([matchClause] 참조). "정확이 0건이면 다시 조회"처럼 나누면 조회가
     * 두 번이 되고, 그 판정에 `ORDER BY`가 붙으면 0건일 때 매칭 집합 전체를 훑어 **3.4초**가
     * 걸린다(실측: 396ms → 3,427ms). 한 질의로 합치면 일치 여부와 무관하게 시간이 일정하다.
     *
     * **오타 허용(`pdb.fuzzy`)은 쓰지 않는다.** 한국어에서 편집거리가 음절 단위라 받침 하나만
     * 바뀌어도 못 잡고("혜장국"→"혜장굼" 0건), 2글자 질의에는 반대로 849건이 걸렸다. 재현율은
     * 못 얻고 `@@@`만 하나 늘어 느려진다.
     */
    @Transactional(readOnly = true)
    override fun searchLikedSpots(query: LikedSpotsSearch, text: String): CursorPage<LikedSpotView> =
        page(query, runSearch(query, text))

    private fun runSearch(query: LikedSpotsSearch, text: String): List<LikedSpotView> {
        val limit = query.first + 1
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
            matchClause(),
        )
        val phrase = SpotSearchJdbcAdapter.toPhrase(text)
        val params = mutableListOf<Any>(
            query.memberId,
            query.language.name,
            query.language.name,
            // 제목·주소 두 자리([matchClause] 참조).
            phrase, phrase,
        )

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

        params += limit

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
     * 제목·주소를 **n-gram 부분 일치**로 본다. 파라미터는 `?` 두 자리.
     *
     * **형태소(lindera) 필드는 쓰지 않는다.** 두 가지 이유가 실측으로 확인됐다:
     *  - **재현율을 더 주지 못한다.** 형태소가 "관광호텔"을 한 단어로 묶어 "관광"을 놓치는데,
     *    n-gram은 그것까지 잡는다. 10개 질의를 대조해 결과 건수가 전부 같았다.
     *  - **압도적으로 느리다.** 같은 질의에서 형태소 3,450ms 대 n-gram 94ms였다.
     *
     * **가중치(boost)를 쓰지 않는다** — `searchSpots`는 순위를 다투므로 제목 3·주소 0.2로
     * 눌러야 하지만, 이쪽은 정렬이 `likedAt DESC` 고정이라 점수가 순서에 관여하지 않는다.
     */
    private fun matchClause(): String =
        "($TITLE_NGRAM_FIELD @@@ ? OR ${SpotSearchJdbcAdapter.ADDRESS_FIELD} @@@ ?)"

    /**
     * 질의 언어에 해당하는 제목 필드 식. BM25 인덱스는 테이블당 하나뿐이라 언어별로 나눌 수
     * 없고, 하나의 인덱스 안에 언어별 토크나이저 필드가 별칭으로 들어 있다(search-node V4).
     * **한국어가 별칭 없는 기본 필드다.**
     */
    private companion object {
        /**
         * 제목의 부분 일치 필드(search-node V2).
         *
         * **언어를 가르지 않는다.** n-gram은 글자 단위라 로케일별 분석기가 필요 없고, 그래서
         * 4개 언어가 이 한 필드를 공유한다. 언어 격리는 조회의 `t.language = ?` 조건이 맡는다.
         */
        const val TITLE_NGRAM_FIELD = "t.title::pdb.alias('title_ng')"
    }
}
