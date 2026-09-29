package org.around.ieuri.community.application.port.out.event

import org.around.ieuri.community.application.query.TypingEventView
import reactor.core.publisher.Flux

/**
 * 대화 실시간 이벤트의 발행·구독(SPEC-0017, POL-048).
 * - 여러 파드 사이를 잇는 공유 브로커가 필요하다 — in-process 이벤트로 바꾸면 운영에서 조용히 유실된다.
 * - 새 메시지 이벤트는 신호일 뿐 전달 보장이 없다. 클라이언트는 재연결 시 조회로 빈 구간을 채운다.
 */
interface ConversationEventPort {
    /** 타이핑 알림. 수신자가 없어도 성공하며, 전달 실패도 예외로 올라가지 않는다. */
    fun publishTyping(event: TypingEventView)

    /** 새 메시지 알림. DB 저장 뒤 발행되며, 발행이 실패해도 전송은 실패하지 않는다. */
    fun publishNewMessage(conversationId: Long, messageId: Long)

    /** 이 대화의 타이핑 이벤트 스트림. 채널당 하나를 공유한다. */
    fun subscribeTyping(conversationId: Long): Flux<TypingEventView>

    /** 이 대화의 새 메시지 스트림. 채널당 하나를 공유한다. */
    fun subscribeNewMessage(conversationId: Long): Flux<Long>
}
