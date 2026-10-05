package org.around.ieuri.community.domain.conversation

/**
 * 대화가 어느 장소의 어느 질문/답변에서 시작됐는지의 스냅샷(SPEC-0026 R14).
 * - 값 복사다. [questionId]·[answerId]로 다시 조회하면 없을 수 있다.
 * - [spotTitle]은 원본 장소명이 바뀌어도 따라가지 않는다(AC-14.3).
 */
data class SpotQaOrigin(
    val catalogId: Long,
    /** 스냅샷 시점의 장소명. */
    val spotTitle: String,
    val questionId: Long,
    /** 질문 본문 앞부분. 최대 [EXCERPT_MAX_LENGTH]자. */
    val questionExcerpt: String,
    val answerId: Long,
    /** 답변 본문 앞부분. 최대 [EXCERPT_MAX_LENGTH]자. */
    val answerExcerpt: String,
) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
        require(questionId > 0) { "questionId는 양수여야 합니다." }
        require(answerId > 0) { "answerId는 양수여야 합니다." }
        require(questionExcerpt.length <= EXCERPT_MAX_LENGTH) {
            "질문 발췌는 ${EXCERPT_MAX_LENGTH}자를 넘을 수 없습니다."
        }
        require(answerExcerpt.length <= EXCERPT_MAX_LENGTH) {
            "답변 발췌는 ${EXCERPT_MAX_LENGTH}자를 넘을 수 없습니다."
        }
    }

    companion object {
        /** 맥락 카드 한 줄 표시용 길이(AC-14.4). */
        const val EXCERPT_MAX_LENGTH = 100

        /** 본문을 발췌 길이로 자른다. 말줄임표는 붙이지 않는다. */
        fun excerpt(body: String): String = body.take(EXCERPT_MAX_LENGTH)
    }
}
