package org.around.ieuri.community.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.TypingEventView
import reactor.core.publisher.Flux

/**
 * 대화 조회 유스케이스(R8·R10).
 *
 * **참여자가 아니면 최상위 errors[]의 FORBIDDEN이다**(AC8-3·AC10-3) — 조회는 볼 자격 자체가
 * 없어 부분 성공이 성립하지 않는다. 뮤테이션의 userErrors와 갈리는 지점이다.
 */
interface ConversationQueryUseCase {
    fun conversations(first: Int?, after: String?, memberId: Long): CursorPage<ConversationView>

    fun conversationMessages(
        conversationId: Long,
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<MessageView>

    /**
     * 타이핑 구독(R10). **구독 시작 시점에 참여자 검사를 한다** — 연결이 살아 있다는 것이
     * 특정 대화를 볼 자격의 증명은 아니다(POL-048). 참여자가 아니면 여기서 예외가 난다.
     *
     * **`Flux`를 그대로 돌려준다.** 구독 표현을 감싸 추상화하려 했으나, 그 래퍼가 채널당
     * 하나여야 할 스트림을 구독마다 새로 만들게 유도해 이벤트 유실을 낳았다. GraphQL 구독은
     * 계약상 Publisher이므로 여기서 Flux를 노출하는 편이 정직하다.
     */
    fun typingIndicator(conversationId: Long, memberId: Long): Flux<TypingEventView>

    /**
     * 새 메시지 구독(R9). 참여자 검사는 타이핑과 같다.
     *
     * **이 구독은 "새 메시지가 있다"는 신호이지 전달 보장 수단이 아니다** — 진실은 DB에
     * 있고, 끊긴 동안의 메시지는 재연결 후 [conversationMessages]로 메꾼다. 메시지를 구독
     * 이벤트로만 전달하면 브로커가 끊긴 순간의 메시지가 영원히 사라진 것처럼 보인다.
     *
     * 이벤트마다 저장된 메시지를 읽어 내려주므로 **본문은 항상 DB의 값**이다.
     */
    fun newMessage(conversationId: Long, memberId: Long): Flux<MessageView>
}
