package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.config.RecommendationProperties
import org.around.ieuri.tourism.application.shared.required.EmbeddingTarget
import org.around.ieuri.tourism.application.shared.required.SpotEmbedding
import org.around.ieuri.tourism.application.shared.required.SpotEmbeddingStorePort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component

/**
 * 장소 임베딩 저장 어댑터(SPEC-0035). `vector` 타입을 JPA가 표현하지 못해 생 SQL이다.
 * - 두 SQL의 LATERAL 조인과 `md5(...)`는 글자까지 같아야 한다 — 다르면 그 행이 매 실행 다시 대상이 된다.
 * - 벡터는 `[0.1,0.2,...]` 문자열로 넘겨 `::vector`로 캐스팅한다.
 */
@Component
class SpotEmbeddingPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
    private val properties: RecommendationProperties,
) : SpotEmbeddingStorePort {

    /**
     * 보이는 번역 행 중 벡터가 없거나, 제목·분류·주소·소개문이 바뀌었거나, 다른 모델로 만든 행. id 오름차순 키셋.
     * - 소개문은 `(catalog_id, language)`의 가장 최근 `spot` 하나에서 온다 — 조인이 행을 불리지 않게 한다.
     */
    override fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget> = exposedJdbcTemplate.query(
        """
            SELECT t.id, t.language, t.title, t.category, t.address, d.overview
            FROM spot_catalog_translation t
            LEFT JOIN LATERAL (
                SELECT d.overview
                FROM spot s JOIN spot_detail d ON d.spot_id = s.id
                WHERE s.catalog_id = t.catalog_id AND s.language = t.language
                  AND NOT d.body_absent AND d.overview IS NOT NULL
                ORDER BY d.fetched_at DESC
                LIMIT 1
            ) d ON true
            LEFT JOIN spot_embedding e ON e.translation_id = t.id
            WHERE t.visible
              AND t.id > ?
              AND (
                    e.translation_id IS NULL
                 OR e.model <> ?
                 OR e.text_hash <> md5(t.title || '|' || t.category || '|' || coalesce(t.address, '') || '|' || left(coalesce(d.overview, ''), ?))
              )
            ORDER BY t.id
            LIMIT ?
        """.trimIndent(),
        { rs, _ ->
            EmbeddingTarget(
                translationId = rs.getLong("id"),
                language = DomainLocale.valueOf(rs.getString("language")),
                title = rs.getString("title"),
                categoryCode = rs.getString("category"),
                address = rs.getString("address"),
                overview = rs.getString("overview"),
            )
        },
        afterTranslationId,
        model,
        properties.embedding.overviewMaxChars,
        limit,
    )

    /** 번역 행에서 해시를 다시 계산해 벡터와 함께 upsert한다. */
    override fun saveAll(embeddings: List<SpotEmbedding>, model: String) {
        if (embeddings.isEmpty()) return
        val setter = ParameterizedPreparedStatementSetter<SpotEmbedding> { ps, embedding ->
            ps.setString(1, model)
            ps.setInt(2, properties.embedding.overviewMaxChars)
            ps.setString(3, embedding.vector.toPgVector())
            ps.setLong(4, embedding.translationId)
        }
        exposedJdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_embedding (translation_id, model, text_hash, embedding, updated_at)
            SELECT t.id,
                   ?,
                   md5(t.title || '|' || t.category || '|' || coalesce(t.address, '') || '|' || left(coalesce(d.overview, ''), ?)),
                   ?::vector,
                   now()
            FROM spot_catalog_translation t
            LEFT JOIN LATERAL (
                SELECT d.overview
                FROM spot s JOIN spot_detail d ON d.spot_id = s.id
                WHERE s.catalog_id = t.catalog_id AND s.language = t.language
                  AND NOT d.body_absent AND d.overview IS NOT NULL
                ORDER BY d.fetched_at DESC
                LIMIT 1
            ) d ON true
            WHERE t.id = ?
            ON CONFLICT (translation_id) DO UPDATE SET
                model      = EXCLUDED.model,
                text_hash  = EXCLUDED.text_hash,
                embedding  = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            embeddings,
            embeddings.size,
            setter,
        )
    }

    private fun FloatArray.toPgVector(): String = joinToString(",", "[", "]") { it.toString() }
}
