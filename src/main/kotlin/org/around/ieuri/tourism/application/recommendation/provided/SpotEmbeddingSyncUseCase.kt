package org.around.ieuri.tourism.application.recommendation.provided

import org.around.ieuri.tourism.application.recommendation.provided.dto.EmbeddingSyncResult
/** 장소 임베딩 적재(SPEC-0035 S2). 배치 단계가 키셋 커서로 부른다. */
interface SpotEmbeddingSyncUseCase {
    /**
     * [afterTranslationId] 다음부터 최대 [limit]건을 임베딩해 저장한다.
     * - 제공자 실패는 단계를 실패시키지 않고 [EmbeddingSyncResult.failed]로 센다.
     */
    fun syncNext(afterTranslationId: Long, limit: Int): EmbeddingSyncResult
}
