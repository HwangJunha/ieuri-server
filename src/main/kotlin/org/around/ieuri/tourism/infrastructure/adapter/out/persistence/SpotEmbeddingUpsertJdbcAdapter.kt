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
 * 텍스트 해시는 **SQL이 행에서 계산한다**([HASH_EXPR]) — 대상 판정과 저장이 같은 식을 쓰므로 앱에서
 * 따로 계산해 어긋날 일이 없다. 소개문(`spot_detail.overview`)도 해시에 들어가 상세가 적재되면 그 행이
 * 다시 대상이 된다. 벡터는 `[0.1,0.2,...]` 문자열로 넘겨 `::vector`로 캐스팅한다.
 */
@Component
class SpotEmbeddingUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotEmbeddingStorePort {

    /** 보이는 번역 행 중 벡터가 없거나, 텍스트가 바뀌었거나, 다른 모델로 만든 행. id 오름차순 키셋. */
    @Transactional(readOnly = true)
    override fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget> =
        jdbcTemplate.query(
            """
            SELECT t.id, t.language, t.title, t.category, t.address, d.overview
            FROM spot_catalog_translation t
            $DETAIL_JOIN
            LEFT JOIN spot_embedding e ON e.translation_id = t.id
            WHERE t.visible
              AND t.id > ?
              AND (e.translation_id IS NULL OR e.model <> ? OR e.text_hash <> $HASH_EXPR)
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

    @Transactional
    override fun saveAll(embeddings: List<SpotEmbedding>, model: String) {
        if (embeddings.isEmpty()) return
        val setter = ParameterizedPreparedStatementSetter<SpotEmbedding> { ps, embedding ->
            ps.setString(1, model)
            ps.setString(2, embedding.vector.toPgVector())
            ps.setLong(3, embedding.translationId)
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, embeddings, embeddings.size, setter)
    }

    private fun FloatArray.toPgVector(): String = joinToString(",", "[", "]") { it.toString() }

    private companion object {
        /**
         * 번역 행의 소개문. 상세는 원본 `spot` 행에 붙어 있고 `(catalog_id, language)`당 `spot`이 둘 이상일 수
         * 있어(실측 13쌍) 가장 최근에 적재된 것 하나만 고른다 — 조인이 행을 불리면 대상이 중복된다.
         */
        const val DETAIL_JOIN = """
            LEFT JOIN LATERAL (
                SELECT d.overview
                FROM spot s JOIN spot_detail d ON d.spot_id = s.id
                WHERE s.catalog_id = t.catalog_id AND s.language = t.language
                  AND NOT d.body_absent AND d.overview IS NOT NULL
                ORDER BY d.fetched_at DESC
                LIMIT 1
            ) d ON true"""

        /** 제목·분류·주소·소개문이 하나라도 바뀌면 값이 달라진다. 저장·대상 판정이 이 식 하나를 공유한다. */
        const val HASH_EXPR =
            "md5(t.title || '|' || t.category || '|' || coalesce(t.address, '') || '|' || coalesce(d.overview, ''))"

        val UPSERT_SQL = """
            INSERT INTO spot_embedding (translation_id, model, text_hash, embedding, updated_at)
            SELECT t.id, ?, $HASH_EXPR, ?::vector, now()
            FROM spot_catalog_translation t
            $DETAIL_JOIN
            WHERE t.id = ?
            ON CONFLICT (translation_id) DO UPDATE SET
                model      = EXCLUDED.model,
                text_hash  = EXCLUDED.text_hash,
                embedding  = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
    }
}
