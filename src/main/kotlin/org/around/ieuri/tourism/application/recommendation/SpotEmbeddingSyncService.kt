package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.tourism.application.recommendation.provided.SpotEmbeddingStoreUseCase
import org.around.ieuri.tourism.application.recommendation.provided.SpotEmbeddingSyncUseCase
import org.around.ieuri.tourism.application.recommendation.provided.dto.EmbeddingSyncResult
import org.around.ieuri.tourism.application.recommendation.required.TextEmbeddingPort
import org.around.ieuri.tourism.domain.recommendation.EmbeddingDimensionMismatchException
import org.around.ieuri.tourism.domain.recommendation.SpotEmbedding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 장소 임베딩 적재(SPEC-0035). 대상 행 → 텍스트 → 제공자 → 저장.
 * - 제공자 호출을 끼고 돌아 자기 트랜잭션이 없다. 대상 읽기와 저장이 각자 트랜잭션이다.
 * - 제공자 실패는 대기 없이 설정 횟수까지 재시도하고(POL-040), 그래도 실패한 묶음은 건너뛰고 센다.
 * - 차원이 설정과 다른 벡터는 저장하지 않는다.
 */
@Service
class SpotEmbeddingSyncService(
    private val storeUseCase: SpotEmbeddingStoreUseCase,
    private val embeddingPort: TextEmbeddingPort,
    private val policy: RecommendationPolicy,
) : SpotEmbeddingSyncUseCase {

    private val log = LoggerFactory.getLogger(SpotEmbeddingSyncService::class.java)

    override fun syncNext(afterTranslationId: Long, limit: Int): EmbeddingSyncResult {
        val inputs = storeUseCase.findInputs(afterTranslationId, limit)
        if (inputs.isEmpty()) return EmbeddingSyncResult(0, 0, 0, afterTranslationId)

        val vectors = embedWithRetry(inputs.map { it.text })
            ?: return EmbeddingSyncResult(inputs.size, 0, inputs.size, inputs.last().translationId)

        val embedding = policy.embedding
        val valid = inputs.zip(vectors).mapNotNull { (input, vector) ->
            try {
                SpotEmbedding.of(input.translationId, embedding.model, embedding.dimension, input.sourceHash, vector)
            } catch (e: EmbeddingDimensionMismatchException) {
                null
            }
        }
        if (valid.size < inputs.size) {
            log.warn("임베딩 차원 불일치로 {}건을 건너뜀(기대 {})", inputs.size - valid.size, embedding.dimension)
        }
        storeUseCase.save(valid)
        return EmbeddingSyncResult(
            processed = inputs.size,
            embedded = valid.size,
            failed = inputs.size - valid.size,
            lastTranslationId = inputs.last().translationId,
        )
    }

    private fun embedWithRetry(texts: List<String>): List<FloatArray>? {
        repeat(policy.embedding.retryCount + 1) { attempt ->
            try {
                return embeddingPort.embed(texts, policy.embedding.model)
            } catch (e: RuntimeException) {
                log.warn("임베딩 호출 실패(시도 {}/{}): {}", attempt + 1, policy.embedding.retryCount + 1, e.javaClass.simpleName)
            }
        }
        return null
    }
}
