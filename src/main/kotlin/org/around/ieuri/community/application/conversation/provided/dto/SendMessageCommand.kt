package org.around.ieuri.community.application.conversation.provided.dto

/** 메시지 전송. 본문·사진·인용 중 최소 하나는 있어야 한다(도메인이 판정). */
data class SendMessageCommand(
    val conversationId: Long,
    val body: String,
    /** 확정된 사진 키. 글과 같은 업로드 경로를 쓴다(POL-042). */
    val photoKey: String? = null,
    /** 인용할 장소 답변. 이미 삭제됐으면 거절하지 않고 인용 없이 보낸다. */
    val sourceAnswerId: Long? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
    }
}
