package org.around.ieuri.community.infrastructure.adapter.out.event.mapper

import org.around.ieuri.community.application.query.TypingEventView
import java.time.Instant

/**
 * 브로커에 실리는 페이로드(SPEC-0017 R14).
 *
 * **JSON으로 직렬화한다.** 이전에는 `senderId|epochMillis` 형태였는데, 같은 타입(숫자) 필드
 * 둘이 **위치로만 구분되어** 순서가 뒤바뀌어도 예외 없이 통과했다 — 1786527431883번 회원이
 * 1970년에 타이핑한 것으로 조용히 읽힌다. 필드 이름이 붙으면 그 사고가 불가능해지고,
 * 나중에 필드를 더해도 구버전 파서가 깨지지 않는다.
 *
 * **본문은 절대 싣지 않는다**(POL-013). 새 메시지는 id만 보내고 구독 시점에 DB에서 읽는다 —
 * 그래야 값의 원본이 DB 하나로 유지되고 개인정보가 브로커를 지나가지 않는다.
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

/**
 * 새 메시지 알림. **id만 담는다** — 본문은 DB에서 읽는다.
 *
 * 필드가 하나뿐이라 뒤바뀔 여지가 없지만 타이핑과 같은 형식으로 둔다. 한쪽만 문자열이면
 * 나중에 필드를 더할 때 그쪽만 규약이 달라진다.
 */
data class NewMessagePayload(val messageId: Long)
