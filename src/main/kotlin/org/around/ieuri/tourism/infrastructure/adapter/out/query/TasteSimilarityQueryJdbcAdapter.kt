package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.TasteSimilarity
import org.around.ieuri.tourism.application.port.out.query.TasteSimilarityQueryPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 취향 유사도 조회 어댑터(SPEC-0035 S2). pgvector의 `avg(vector)`와 코사인 거리(`<=>`)를 QueryDSL로 표현할
 * 수 없어 이 어댑터에만 생 SQL이 있다. 회원의 좋아요·싫어요 장소 벡터 평균을 CTE로 만들고 후보와의
 * 코사인 유사도(`1 − 거리`)만 돌려준다 — 벡터는 DB 밖으로 나오지 않는다.
 *
 * 비어 있는 id 집합은 `WHERE false`로 바꾼다 — 평균은 행 하나(NULL)를 돌려주고 유사도가 NULL이 된다.
 */
@Component
class TasteSimilarityQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : TasteSimilarityQueryPort {

    @Transactional(readOnly = true)
    override fun findSimilarities(
        likedCatalogIds: Set<Long>,
        dislikedCatalogIds: Set<Long>,
        candidateCatalogIds: Collection<Long>,
        language: DomainLocale,
        model: String,
    ): Map<Long, TasteSimilarity> {
        if (candidateCatalogIds.isEmpty()) return emptyMap()
        val params = mutableListOf<Any>()
        val liked = centroidClause(likedCatalogIds, language, model, params)
        val disliked = centroidClause(dislikedCatalogIds, language, model, params)
        params += model
        params += language.name
        params.addAll(candidateCatalogIds)

        val sql = """
            WITH liked AS ($liked), disliked AS ($disliked)
            SELECT t.catalog_id,
                   CASE WHEN liked.v IS NULL THEN NULL ELSE 1 - (e.embedding <=> liked.v) END AS like_similarity,
                   CASE WHEN disliked.v IS NULL THEN NULL ELSE 1 - (e.embedding <=> disliked.v) END AS dislike_similarity
            FROM spot_embedding e
            JOIN spot_catalog_translation t ON t.id = e.translation_id
            CROSS JOIN liked
            CROSS JOIN disliked
            WHERE e.model = ? AND t.language = ? AND t.catalog_id IN (${placeholders(candidateCatalogIds.size)})
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                rs.getLong("catalog_id") to TasteSimilarity(
                    like = rs.getDouble("like_similarity").takeUnless { rs.wasNull() },
                    dislike = rs.getDouble("dislike_similarity").takeUnless { rs.wasNull() },
                )
            },
            *params.toTypedArray(),
        ).toMap()
    }

    /** 반응 장소들의 벡터 평균. 집합이 비면 조건을 `false`로 두어 NULL 한 행이 나온다. */
    private fun centroidClause(ids: Set<Long>, language: DomainLocale, model: String, params: MutableList<Any>): String {
        params += model
        params += language.name
        val filter = if (ids.isEmpty()) {
            "false"
        } else {
            params.addAll(ids)
            "t.catalog_id IN (${placeholders(ids.size)})"
        }
        return """
            SELECT avg(e.embedding) AS v
            FROM spot_embedding e JOIN spot_catalog_translation t ON t.id = e.translation_id
            WHERE e.model = ? AND t.language = ? AND $filter
        """.trimIndent()
    }

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(",")
}
