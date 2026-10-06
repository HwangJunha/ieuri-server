package org.around.ieuri.tourism.application.shared

import org.around.ieuri.tourism.application.shared.RecommendationPolicy
import org.around.ieuri.tourism.application.shared.provided.EmbeddingSyncResult
import org.around.ieuri.tourism.application.shared.provided.SpotEmbeddingSyncUseCase
import org.around.ieuri.tourism.application.shared.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.shared.required.EmbeddingTarget
import org.around.ieuri.tourism.application.shared.required.SpotEmbedding
import org.around.ieuri.tourism.application.shared.required.SpotEmbeddingStorePort
import org.around.ieuri.tourism.application.shared.required.TextEmbeddingPort
import org.around.ieuri.tourism.domain.shared.EmbeddingText
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 장소 임베딩 적재(SPEC-0035). 대상 행 → 텍스트 → 제공자 → 저장.
 * - 제공자 실패는 대기 없이 설정 횟수까지 재시도하고(POL-040), 그래도 실패한 묶음은 건너뛰고 센다.
 * - 차원이 설정과 다른 벡터는 저장하지 않는다.
 */
@Service
class SpotEmbeddingSyncService(
    private val storePort: SpotEmbeddingStorePort,
    private val embeddingPort: TextEmbeddingPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
    private val policy: RecommendationPolicy,
) : SpotEmbeddingSyncUseCase {

    private val log = LoggerFactory.getLogger(SpotEmbeddingSyncService::class.java)

    override fun syncNext(afterTranslationId: Long, limit: Int): EmbeddingSyncResult {
        val embedding = policy.embedding
        val targets = storePort.findTargets(afterTranslationId, limit, embedding.model)
        if (targets.isEmpty()) return EmbeddingSyncResult(0, 0, 0, afterTranslationId)

        val texts = textsOf(targets)
        val vectors = embedWithRetry(texts)
        if (vectors == null) {
            return EmbeddingSyncResult(targets.size, 0, targets.size, targets.last().translationId)
        }

        val valid = targets.zip(vectors).filter { (_, vector) -> vector.size == embedding.dimension }
        if (valid.size < targets.size) {
            log.warn("임베딩 차원 불일치로 {}건을 건너뜀(기대 {})", targets.size - valid.size, embedding.dimension)
        }
        storePort.saveAll(valid.map { (target, vector) -> SpotEmbedding(target.translationId, vector) }, embedding.model)
        return EmbeddingSyncResult(
            processed = targets.size,
            embedded = valid.size,
            failed = targets.size - valid.size,
            lastTranslationId = targets.last().translationId,
        )
    }

    /** 카테고리 라벨은 행의 언어로. 언어별로 한 번씩만 조회한다. */
    private fun textsOf(targets: List<EmbeddingTarget>): List<String> {
        val labels = targets.groupBy { it.language }.mapValues { (language, rows) ->
            appCategoryQueryPort.findDisplays(rows.map { it.categoryCode }.toSet(), language)
        }
        return targets.map { target ->
            val label = labels.getValue(target.language)[target.categoryCode]?.label ?: target.categoryCode
            EmbeddingText.of(target.title, label, target.address, target.overview, policy.embedding.overviewMaxChars)
        }
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
