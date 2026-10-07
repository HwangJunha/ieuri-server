package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.recommendation.provided.EmbeddingInput
import org.around.ieuri.tourism.application.recommendation.provided.SpotEmbeddingStoreUseCase
import org.around.ieuri.tourism.application.recommendation.required.SpotEmbedding
import org.around.ieuri.tourism.application.recommendation.required.SpotEmbeddingStorePort
import org.around.ieuri.tourism.domain.recommendation.EmbeddingText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 임베딩 대상 읽기와 벡터 저장. 텍스트의 카테고리 라벨은 행의 언어로, 언어별로 한 번씩만 조회한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotEmbeddingStoreService(
    private val storePort: SpotEmbeddingStorePort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
    private val policy: RecommendationPolicy,
) : SpotEmbeddingStoreUseCase {

    override fun findInputs(afterTranslationId: Long, limit: Int): List<EmbeddingInput> {
        val embedding = policy.embedding
        val targets = storePort.findTargets(afterTranslationId, limit, embedding.model)
        val labels = targets.groupBy { it.language }.mapValues { (language, rows) ->
            appCategoryQueryPort.findDisplays(rows.map { it.categoryCode }.toSet(), language)
        }
        return targets.map { target ->
            val label = labels.getValue(target.language)[target.categoryCode]?.label ?: target.categoryCode
            EmbeddingInput(target.translationId, target.sourceHash, EmbeddingText.of(target.title, label, target.address, target.overview, embedding.overviewMaxChars))
        }
    }

    override fun save(embeddings: List<SpotEmbedding>) = storePort.saveAll(embeddings, policy.embedding.model)
}
