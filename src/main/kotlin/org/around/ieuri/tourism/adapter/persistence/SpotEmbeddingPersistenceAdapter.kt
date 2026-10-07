package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.common.persistence.vectorParam
import org.around.ieuri.tourism.adapter.config.RecommendationProperties
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotEmbeddingTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.shared.required.EmbeddingTarget
import org.around.ieuri.tourism.application.shared.required.SpotEmbedding
import org.around.ieuri.tourism.application.shared.required.SpotEmbeddingStorePort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.substring
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Component

/**
 * 장소 임베딩 저장 어댑터(SPEC-0035).
 * - 원문 지문은 `md5(제목|분류|주소|소개문 앞 N자)`다. 이미 저장된 지문과 같은 식이어야 한다 — 다르면 모든 행이 매 실행 다시 대상이 된다.
 */
@Component
class SpotEmbeddingPersistenceAdapter(
    private val properties: RecommendationProperties,
) : SpotEmbeddingStorePort {

    /**
     * 보이는 번역 행 중 벡터가 없거나, 원문 지문이 바뀌었거나, 다른 모델로 만든 행. id 오름차순 키셋.
     * - 소개문은 `(catalog_id, language)`의 가장 최근에 확보한 원본 하나에서 온다.
     */
    override fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget> {
        val t = SpotCatalogTranslationTable
        val overview = wrapAsExpression<String>(
            SpotTable.join(SpotDetailTable, JoinType.INNER, SpotTable.id, SpotDetailTable.spotId)
                .select(SpotDetailTable.overview)
                .where {
                    (SpotTable.catalogId eq t.catalogId) and (SpotTable.language eq t.language) and
                        (SpotDetailTable.bodyAbsent eq false) and SpotDetailTable.overview.isNotNull()
                }
                .orderBy(SpotDetailTable.fetchedAt, SortOrder.DESC)
                .limit(1),
        )
        val sourceHash = CustomFunction<String>(
            "md5",
            TextColumnType(),
            concat(
                "|",
                listOf(
                    t.title,
                    t.category,
                    coalesce(t.address, stringLiteral("")),
                    CustomFunction<String>("COALESCE", TextColumnType(), overview, stringLiteral("")).substring(1, properties.embedding.overviewMaxChars),
                ),
            ),
        )
        return t.join(SpotEmbeddingTable, JoinType.LEFT, t.id, SpotEmbeddingTable.translationId)
            .select(t.id, t.language, t.title, t.category, t.address, overview, sourceHash)
            .where {
                (t.visible eq true) and (t.id greater afterTranslationId) and
                    (SpotEmbeddingTable.translationId.isNull() or (SpotEmbeddingTable.model neq model) or (SpotEmbeddingTable.textHash neq sourceHash))
            }
            .orderBy(t.id, SortOrder.ASC)
            .limit(limit)
            .map {
                EmbeddingTarget(
                    translationId = it[t.id],
                    sourceHash = it[sourceHash],
                    language = DomainLocale.valueOf(it[t.language]),
                    title = it[t.title],
                    categoryCode = it[t.category],
                    address = it[t.address],
                    overview = it[overview],
                )
            }
    }

    /** 벡터 값은 캐스트가 필요해 한 행씩 upsert한다(호출당 수십 건). */
    override fun saveAll(embeddings: List<SpotEmbedding>, model: String) {
        embeddings.forEach { embedding ->
            SpotEmbeddingTable.upsert(
                SpotEmbeddingTable.translationId,
                onUpdate = {
                    it[SpotEmbeddingTable.model] = model
                    it[SpotEmbeddingTable.textHash] = embedding.sourceHash
                    it[SpotEmbeddingTable.embedding] = vectorParam(embedding.vector)
                    it[SpotEmbeddingTable.updatedAt] = CurrentTimestampWithTimeZone
                },
            ) {
                it[SpotEmbeddingTable.translationId] = embedding.translationId
                it[SpotEmbeddingTable.model] = model
                it[SpotEmbeddingTable.textHash] = embedding.sourceHash
                it[SpotEmbeddingTable.embedding] = vectorParam(embedding.vector)
            }
        }
    }
}
