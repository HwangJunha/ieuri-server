package org.around.ieuri.community.application.command

/**
 * 장소 질문/답변 쓰기 입력(SPEC-0026).
 *
 * 커뮤니티의 다른 커맨드와 같이 **memberId를 담지 않는다** — 호출자 신원은 인증 컨텍스트에서
 * 오며 커맨드는 입력만 들고 있다.
 */

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

/**
 * 해결 상태 변경(R8). **양방향이다** — [resolved]가 false면 미해결로 되돌린다.
 *
 * 불리언을 받는 이유는 뮤테이션을 둘로 나누지 않기 위해서다. 해제 전용 뮤테이션을 두면
 * 소유자 판정·멱등 규약이 두 벌이 되고 갈라진다.
 */
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
