package org.around.ieuri.community.application.port.out.event

import org.around.ieuri.community.application.query.TypingEventView
import reactor.core.publisher.Flux

/**
 * 대화 실시간 이벤트의 발행·구독(SPEC-0017 R10·R14, POL-048).
 *
 * **공유 브로커를 전제한다.** 서비스는 k3s에서 여러 파드로 운영되므로 in-process 이벤트
 * (POL-028의 Spring ApplicationEvent)로는 성립하지 않는다 — 발신자가 파드 A에, 수신자가
 * 파드 B에 연결되어 있으면 이벤트가 도달하지 않는다. 그 실패는 조용해서 단일 인스턴스
 * 개발 환경에서는 완벽히 동작하고 운영에서만 "가끔 되는 기능"이 된다.
 *
 * **두 이벤트의 성질이 다르다.**
 *
 * | | 타이핑 | 새 메시지 |
 * |---|---|---|
 * | 유실 허용 | 허용 | **불가** |
 * | 영속화 | 안 함 | DB가 원본 |
 * | 끊긴 동안의 이벤트 | 버린다 | 재연결 후 **조회로 메꾼다** |
 *
 * 그래서 새 메시지 이벤트는 **"새 메시지가 있다"는 신호일 뿐 전달 보장 수단이 아니다** —
 * 진실은 항상 DB에 있고, 클라이언트는 재연결 시 conversationMessages로 빈 구간을 채운다.
 * 이 규약이 없으면 브로커가 끊긴 순간의 메시지가 영원히 사라진 것처럼 보인다.
 */
interface ConversationEventPort {
    /**
     * 타이핑 알림. **수신자가 없어도 성공한다**(AC10-2) — 받을 사람이 없는 것은 실패가
     * 아니다. 전달 실패도 호출자에게 예외로 올라가지 않는다.
     */
    fun publishTyping(event: TypingEventView)

    /**
     * 새 메시지 알림. 메시지는 **이미 DB에 저장된 뒤** 발행된다 — 발행이 실패해도 메시지는
     * 남으므로 전송 자체가 실패하지 않는다. 구독자는 이 신호를 받아 화면을 갱신한다.
     */
    fun publishNewMessage(conversationId: Long, messageId: Long)

    /**
     * 이 대화의 타이핑 이벤트 스트림. **채널당 하나를 공유한다** — 구독마다 새로 만들면
     * 같은 채널에 리스너가 여러 개 붙어 이벤트가 엉뚱한 쪽으로 흘러간다.
     */
    fun subscribeTyping(conversationId: Long): Flux<TypingEventView>

    /** 이 대화의 새 메시지 스트림. 채널당 하나를 공유한다. */
    fun subscribeNewMessage(conversationId: Long): Flux<Long>
}
