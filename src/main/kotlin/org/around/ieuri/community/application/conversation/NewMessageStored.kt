package org.around.ieuri.community.application.conversation

/**
 * 새 메시지가 저장됐다(SPEC-0017 R14). 브로커 발행을 커밋 뒤로 미루는 데 쓴다.
 * - 트랜잭션 안에서 발행하면 구독자가 커밋 전 행을 못 읽어 이벤트가 조용히 버려진다.
 * - 본문은 싣지 않는다(POL-013).
 */
data class NewMessageStored(
    val conversationId: Long,
    val messageId: Long,
)
