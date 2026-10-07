package org.around.ieuri.community.application.conversation.provided.dto

import java.time.Instant

/** 타이핑 이벤트(R10). 영속화되지 않는 전달 시점의 스냅샷이다. */
data class TypingEventView(
    val conversationId: Long,
    val senderId: Long,
    val emittedAt: Instant,
)
