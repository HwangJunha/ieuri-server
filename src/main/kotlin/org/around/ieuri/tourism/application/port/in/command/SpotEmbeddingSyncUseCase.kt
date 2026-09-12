package org.around.ieuri.tourism.application.port.`in`.command

/**
 * 장소 임베딩 적재(SPEC-0035 S2). 배치 단계가 키셋 커서로 부른다. 초기 적재·모델 교체·증분이 같은
 * 경로다 — 대상 판정이 한 규칙이기 때문이다.
 */
interface SpotEmbeddingSyncUseCase {
    /**
     * [afterTranslationId] 다음부터 최대 [limit]건을 임베딩해 저장한다. 제공자 실패는 단계를 실패시키지
     * 않고 [EmbeddingSyncResult.failed]로 센다 — 그 행은 다음 실행의 대상으로 남는다.
     */
    fun syncNext(afterTranslationId: Long, limit: Int): EmbeddingSyncResult
}

/** [processed]가 0이면 소진이다. [lastTranslationId]는 다음 호출의 커서. */
data class EmbeddingSyncResult(
    val processed: Int,
    val embedded: Int,
    val failed: Int,
    val lastTranslationId: Long,
)
