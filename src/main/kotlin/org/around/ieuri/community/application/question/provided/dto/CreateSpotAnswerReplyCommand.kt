package org.around.ieuri.community.application.question.provided.dto

data class CreateSpotAnswerReplyCommand(
    /** 답글을 달 대상 답변. 답글 id를 넘기면 REPLY_TO_REPLY_NOT_ALLOWED다. */
    val parentAnswerId: Long,
    val body: String,
    val photoKeys: List<String>,
) {
    init {
        require(parentAnswerId > 0) { "parentAnswerId는 양수여야 합니다." }
    }
}
