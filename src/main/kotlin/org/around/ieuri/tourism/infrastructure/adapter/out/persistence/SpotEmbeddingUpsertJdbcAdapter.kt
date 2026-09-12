package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.EmbeddingTarget
import org.around.ieuri.tourism.application.port.out.persistence.SpotEmbedding
import org.around.ieuri.tourism.application.port.out.persistence.SpotEmbeddingStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 장소 임베딩 저장 어댑터(SPEC-0035 S2). `vector` 타입을 JPA가 표현하지 못해 이 어댑터에만 생 SQL이 있다.
 *
 * 두 SQL은 같은 두 조각을 담고 있다 — 소개문을 붙이는 LATERAL 조인과 텍스트 해시 `md5(...)`. **둘이 글자까지
 * 같아야 한다.** 대상 판정이 계산한 해시와 저장한 해시가 다르면 그 행은 매 실행 다시 대상이 된다.
 * 벡터는 `[0.1,0.2,...]` 문자열로 넘겨 `::vector`로 캐스팅한다.
 */
@Component
class SpotEmbeddingUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotEmbeddingStorePort {

    /**
     * 보이는 번역 행 중 벡터가 없거나, 제목·분류·주소·소개문이 바뀌었거나, 다른 모델로 만든 행. id 오름차순 키셋.
     *
     * 소개문은 원본 `spot` 행의 상세에서 온다. `(catalog_id, language)`당 `spot`이 둘 이상일 수 있어(실측 13쌍)
     * 가장 최근에 적재된 하나만 고른다 — 조인이 행을 불리면 대상이 중복된다.
     */
    @Transactional(readOnly = true)
    override fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget> =
        jdbcTemplate.query(
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
                 OR e.text_hash <> md5(t.title || '|' || t.category || '|' || coalesce(t.address, '') || '|' || coalesce(d.overview, ''))
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
            afterTranslationId, model, limit,
        )

    /** 번역 행에서 해시를 다시 계산해 벡터와 함께 upsert한다. */
    @Transactional
    override fun saveAll(embeddings: List<SpotEmbedding>, model: String) {
        if (embeddings.isEmpty()) return
        val setter = ParameterizedPreparedStatementSetter<SpotEmbedding> { ps, embedding ->
            ps.setString(1, model)
            ps.setString(2, embedding.vector.toPgVector())
            ps.setLong(3, embedding.translationId)
        }
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_embedding (translation_id, model, text_hash, embedding, updated_at)
            SELECT t.id,
                   ?,
                   md5(t.title || '|' || t.category || '|' || coalesce(t.address, '') || '|' || coalesce(d.overview, '')),
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
