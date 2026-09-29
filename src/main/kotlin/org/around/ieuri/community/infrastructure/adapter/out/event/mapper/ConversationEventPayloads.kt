package org.around.ieuri.community.infrastructure.adapter.out.event.mapper

import org.around.ieuri.community.application.query.TypingEventView
import java.time.Instant

/**
 * 브로커에 JSON으로 실리는 타이핑 페이로드(SPEC-0017 R14).
 * - 본문은 절대 싣지 않는다(POL-013).
 */
data class TypingEventPayload(
    val senderId: Long,
    val emittedAtEpochMilli: Long,
) {
    fun toView(conversationId: Long): TypingEventView = TypingEventView(
        conversationId = conversationId,
        senderId = senderId,
        emittedAt = Instant.ofEpochMilli(emittedAtEpochMilli),
    )

    companion object {
        fun from(event: TypingEventView): TypingEventPayload = TypingEventPayload(
            senderId = event.senderId,
            emittedAtEpochMilli = event.emittedAt.toEpochMilli(),
        )
    }
}

/** 새 메시지 알림. id만 담고 본문은 구독 측이 DB에서 읽는다. */
data class NewMessagePayload(val messageId: Long)
