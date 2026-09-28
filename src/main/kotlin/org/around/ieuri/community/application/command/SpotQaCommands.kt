package org.around.ieuri.community.application.command

// 장소 질문/답변 쓰기 입력(SPEC-0026). memberId는 담지 않는다.

data class CreateSpotQuestionCommand(
    val catalogId: Long,
    val body: String,
    val photoKeys: List<String>,
) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}

data class DeleteSpotQuestionCommand(val questionId: Long) {
    init {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
    }
}

/** 해결 상태 변경(R8). [resolved]가 false면 미해결로 되돌린다. */
data class ResolveSpotQuestionCommand(
    val questionId: Long,
    val resolved: Boolean,
) {
    init {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
    }
}

data class CreateSpotAnswerCommand(
    val questionId: Long,
    val body: String,
    val photoKeys: List<String>,
) {
    init {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
    }
}

data class DeleteSpotAnswerCommand(val answerId: Long) {
    init {
        require(answerId > 0) { "answerId는 양수여야 합니다." }
    }
}

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

data class DeleteSpotAnswerReplyCommand(val replyId: Long) {
    init {
        require(replyId > 0) { "replyId는 양수여야 합니다." }
    }
}
