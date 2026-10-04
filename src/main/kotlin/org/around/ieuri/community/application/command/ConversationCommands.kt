package org.around.ieuri.community.application.command

import org.around.ieuri.community.domain.shared.ReportReason

// DM 쓰기 입력. 형식 검증만 하고 업무 불변식은 도메인이 판정한다. memberId는 담지 않는다.

/** 대화 시작. 상대를 회원으로 직접 지목한다(SPEC-0021 R2). */
data class StartConversationCommand(val counterpartMemberId: Long) {
    init {
        require(counterpartMemberId > 0) { "counterpartMemberId는 양수여야 합니다." }
    }
}

/** 메시지 전송. 본문과 사진 중 최소 하나는 있어야 한다(도메인이 판정). */
data class SendMessageCommand(
    val conversationId: Long,
    val body: String,
    /** 확정된 사진 키. 글과 같은 업로드 경로를 쓴다(POL-042). */
    val photoKey: String? = null,
    /** 인용할 장소 답변(SPEC-0026 R13). 이미 삭제됐으면 거절하지 않고 인용 없이 보낸다. */
    val sourceAnswerId: Long? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
    }
}

data class NotifyTypingCommand(val conversationId: Long) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
    }
}

/** 대화 읽음 처리. [upToMessageId]가 null이면 마지막 메시지까지 읽은 것으로 본다(모두 읽음). */
data class MarkConversationReadCommand(
    val conversationId: Long,
    val upToMessageId: Long? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
        require(upToMessageId == null || upToMessageId > 0) { "upToMessageId는 양수여야 합니다." }
    }
}

/** 메시지 다건 신고(SPEC-0017 R15). 빈 목록·대화 혼재 판정은 서비스가 저장소를 물어서 한다. */
data class ReportMessagesCommand(
    val messageIds: List<Long>,
    val reason: ReportReason,
    val detail: String?,
)
