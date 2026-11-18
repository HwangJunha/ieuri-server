package org.around.ieuri.tourism.application.recommendation.required

import org.around.ieuri.tourism.application.recommendation.required.dto.EmbeddingTarget

/** 임베딩을 새로 만들 번역 행 조회(SPEC-0035 S2). */
interface EmbeddingTargetQueryPort {
    /**
     * 임베딩이 필요한 번역 행을 id 오름차순으로 [limit]건. [afterTranslationId]보다 큰 id만(키셋).
     * - 벡터 없음·제목·분류·주소·소개문 변경·[model] 불일치인 행이 대상이다.
     */
    fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget>
}
