package org.around.ieuri.community.application.command

import org.around.ieuri.community.domain.vo.ReportReason

/**
 * 피드·DM 쓰기 입력(SPEC-0017). 형식 검증만 여기서 하고, 업무 불변식은 도메인이 판정한다.
 * 호출자(memberId)는 인증 컨텍스트에서 오므로 커맨드에 담지 않는다.
 */

/**
 * 피드 작성. **catalogId와 photoKeys가 모두 필수다** — 개수 판정은 도메인(FeedItem.create)이
 * 하므로 여기서는 형식만 본다.
 */
data class CreateFeedItemCommand(
    val body: String,
    val catalogId: Long,
    /** 확정 키. 배열 순서가 표시 순서다. */
    val photoKeys: List<String>,
) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}

data class LikeFeedItemCommand(val feedItemId: Long) {
    init {
        require(feedItemId > 0) { "feedItemId는 양수여야 합니다." }
    }
}

/**
 * 대화 시작. **입력이 회원 id가 아니라 피드 id다** — 이 선택이 "피드 작성자에게만 대화를
 * 걸 수 있다"(R7)를 구조적으로 강제한다. 커뮤니티 글 작성자를 가리킬 방법이 애초에 없다.
 */
data class StartConversationCommand(val feedItemId: Long) {
    init {
        require(feedItemId > 0) { "feedItemId는 양수여야 합니다." }
    }
}

/**
 * 메시지 전송. **본문·피드 인용·사진 중 최소 하나는 있어야 한다** — 셋 다 비면 보낼 것이
 * 없는 전송이며, 그 판정은 도메인이 한다.
 */
data class SendMessageCommand(
    val conversationId: Long,
    val body: String,
    /** 인용할 피드. 인스타 스토리 답장과 같은 방식으로 **메시지에** 붙는다. */
    val feedItemId: Long? = null,
    /** 확정된 사진 키. 글·피드와 같은 업로드 경로를 쓴다(POL-042). */
    val photoKey: String? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
        require(feedItemId == null || feedItemId > 0) { "feedItemId는 양수여야 합니다." }
    }
}

data class NotifyTypingCommand(val conversationId: Long) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
    }
}

/**
 * 대화 읽음 처리. [upToMessageId]가 null이면 그 대화의 마지막 메시지까지 읽은 것으로 본다 —
 * 클라이언트가 "모두 읽음"을 표현하는 방법이며, 마지막 id를 몰라도 부를 수 있다.
 */
data class MarkConversationReadCommand(
    val conversationId: Long,
    val upToMessageId: Long? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
        require(upToMessageId == null || upToMessageId > 0) { "upToMessageId는 양수여야 합니다." }
    }
}

/** 메시지 다건 신고(R15). 빈 목록·대화 혼재 판정은 서비스가 저장소를 물어서 한다. */
data class ReportMessagesCommand(
    val messageIds: List<Long>,
    val reason: ReportReason,
    val detail: String?,
)
