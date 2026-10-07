package org.around.ieuri.tourism.application.recommendation

/** 임베딩 사양. [model]·[dimension]이 저장된 벡터와 다르면 그 벡터는 없는 것으로 본다(재적재 대상). */
data class EmbeddingSpec(
    val model: String,
    val dimension: Int,
    /** 제공자 실패 시 즉시 재시도 횟수. 시간 기반 대기는 없다(POL-040). */
    val retryCount: Int,
    /** 임베딩 텍스트에 넣는 소개문 길이 상한(자). 토큰 비용의 천장이다. */
    val overviewMaxChars: Int,
) {
    init {
        require(model.isNotBlank()) { "임베딩 모델이 비어 있습니다." }
        require(dimension >= 1) { "임베딩 차원은 1 이상이어야 합니다." }
        require(retryCount >= 0) { "재시도 횟수는 음수일 수 없습니다." }
        require(overviewMaxChars >= 0) { "소개문 길이 상한은 음수일 수 없습니다." }
    }
}
