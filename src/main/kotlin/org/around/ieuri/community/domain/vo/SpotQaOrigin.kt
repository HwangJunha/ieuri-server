package org.around.ieuri.community.domain.vo

/**
 * 대화가 어느 장소의 어느 질문/답변에서 시작됐는지의 스냅샷(SPEC-0026 R14).
 *
 * **값 복사이며 살아있는 참조가 아니다.** 링크만 걸면 원본이 삭제되는 순간 맥락이 사라지는데,
 * 맥락을 남기는 것이 이 값의 존재 이유이므로 그러면 목적을 달성하지 못한다. 따라서
 * [questionId]·[answerId]는 값으로 남지만 그 id로 다시 조회하면 없을 수 있다.
 *
 * [spotTitle]도 스냅샷 시점의 값이라 장소명이 나중에 바뀌어도 따라가지 않는다(AC-14.3).
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

        /** 본문을 발췌 길이로 자른다. 짧으면 그대로다 — 자를 때 말줄임표를 붙이지 않는다. */
        fun excerpt(body: String): String = body.take(EXCERPT_MAX_LENGTH)
    }
}
