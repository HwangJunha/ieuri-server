package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.tourism.application.category.provided.CategoryQueryUseCase
import org.around.ieuri.tourism.application.recommendation.provided.SpotEmbeddingStoreUseCase
import org.around.ieuri.tourism.application.recommendation.provided.dto.EmbeddingInput
import org.around.ieuri.tourism.application.recommendation.required.SpotEmbeddingRepository
import org.around.ieuri.tourism.domain.recommendation.EmbeddingText
import org.around.ieuri.tourism.domain.recommendation.SpotEmbedding
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 임베딩 대상 읽기와 벡터 저장. 텍스트의 카테고리 라벨은 카테고리 조회를 재사용해 행의 언어로, 언어별로 한 번씩만 조회한다. */
@Service
@Transactional
class SpotEmbeddingStoreService(
    private val embeddingRepository: SpotEmbeddingRepository,
    private val categoryQuery: CategoryQueryUseCase,
    private val policy: RecommendationPolicy,
) : SpotEmbeddingStoreUseCase {

    override fun findInputs(afterTranslationId: Long, limit: Int): List<EmbeddingInput> {
        val embedding = policy.embedding
        val targets = embeddingRepository.findTargets(afterTranslationId, limit, embedding.model)
        val labels = targets.groupBy { it.language }.mapValues { (language, rows) ->
            categoryQuery.categoryDisplays(rows.map { it.categoryCode }.toSet(), language)
        }
        return targets.map { target ->
            // categoryDisplays는 요청한 모든 코드에 값이 있다 — 정의가 없으면 코드 그대로가 라벨이다.
            val label = labels.getValue(target.language).getValue(target.categoryCode).label
            EmbeddingInput(target.translationId, target.sourceHash, EmbeddingText.of(target.title, label, target.address, target.overview, embedding.overviewMaxChars))
        }
    }

    override fun save(embeddings: List<SpotEmbedding>) = embeddingRepository.saveAll(embeddings)
}
