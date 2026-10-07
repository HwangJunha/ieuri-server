package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.required.LikedSpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.LikedSpotsSearch
import org.around.ieuri.tourism.application.shared.exception.InvalidCursorException
import org.around.ieuri.tourism.domain.catalog.LikedSpotCursor
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * 좋아요 목록 내 검색 어댑터(SPEC-0033). pg_search 연산자(`@@@`) 때문에 생 SQL이다.
 * - 검색 노드(searchJdbcTemplate)를 본다. 방금 누른 좋아요는 복제 전이면 잠시 검색에 안 잡힌다.
 * - `spot_reaction`을 조인하므로 매칭 집합 전체를 읽는다 — 넓은 검색어일수록 느려진다(정렬이 고정이라 top-K 조기 종료는 애초에 못 쓴다).
 */
@Component
class LikedSpotSearchQueryAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : LikedSpotSearchPort {

    /**
     * 좋아요 중 [text]에 일치하는 한 페이지. `likedAt DESC, catalogId DESC` 고정이다.
     * - BM25 점수는 계산하지도 정렬에 쓰지도 않는다(SPEC-0033 R3). 오타 허용은 없다.
     * - 질의는 한 번이다 — 폴백 재조회를 하지 않는다.
     */
    override fun searchLikedSpots(query: LikedSpotsSearch, text: String): CursorPage<LikedSpotView> = page(query, runSearch(query, text))

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
        val phrase = SpotSearchQueryAdapter.toPhrase(text)
        val params = mutableListOf<Any>(
            query.memberId,
            query.language.name,
            query.language.name,
            // 제목·주소 두 자리([matchClause] 참조).
            phrase,
            phrase,
        )

        if (!query.categories.isUnfiltered) {
            // `searchSpots`와 같은 출처인 번역 행의 분류 사본으로 거른다.
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

        // 카드 값을 조인 한 번으로 함께 읽는다 — 나눠 읽으면 행이 빠질 때 `hasNextPage`가 틀어진다.
        // `visible`을 걸지 않는다 — 원본이 사라진 장소의 좋아요도 보인다(메인 DB 목록과 같은 규칙).
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
                // 클라이언트에 나가는 id는 카탈로그 id다(SPEC-0019).
                id = catalogId,
                title = rs.getString("title"),
                thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                address = rs.getString("address"),
                category = SpotCategory(rs.getString("category")),
                copyright = copyright,
                // copyright에서 도출되는 값이라 적재하지 않는다.
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
            // 좋아요 목록은 총 개수를 노출하지 않는다(null).
            totalCount = null,
        )
    }

    /** 제목·주소의 n-gram 부분 일치(가중치 없음). 파라미터는 `?` 두 자리다. */
    private fun matchClause(): String = "($TITLE_NGRAM_FIELD @@@ ? OR ${SpotSearchQueryAdapter.ADDRESS_FIELD} @@@ ?)"

    private companion object {
        /** 제목의 n-gram 부분 일치 필드(search-node V2). 4개 언어가 공유하며, 언어 격리는 `t.language = ?`가 맡는다. */
        const val TITLE_NGRAM_FIELD = "t.title::pdb.alias('title_ng')"
    }
}
