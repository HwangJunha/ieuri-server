package org.around.ieuri.community.application.conversation.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import reactor.core.publisher.Flux

/** 대화 조회 유스케이스(R8·R10). 참여자가 아니면 최상위 errors[]의 FORBIDDEN이다. */
interface ConversationQueryUseCase {
    fun conversations(first: Int?, after: String?, memberId: Long): CursorPage<ConversationView>

    fun conversationMessages(
        conversationId: Long,
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<MessageView>

    /** 타이핑 구독(R10). 구독 시작 시점에 참여자 검사를 하며, 아니면 여기서 예외가 난다(POL-048). */
    fun typingIndicator(conversationId: Long, memberId: Long): Flux<TypingEventView>

    /**
     * 새 메시지 구독(R9)의 메시지 id 스트림. 참여자 검사는 타이핑과 같다. 본문은 [message]로 이벤트마다 DB에서 읽는다.
     * - 전달 보장이 없다 — 끊긴 동안의 메시지는 재연결 후 [conversationMessages]로 메꾼다.
     * - 주의: 스트림 안에서 DB를 읽지 않는다 — 이 메서드의 트랜잭션은 반환과 함께 끝난다. 읽기는 [message]를 호출해 자기 트랜잭션에서 한다.
     */
    fun newMessageIds(conversationId: Long, memberId: Long): Flux<Long>

    /** 메시지 단건. 대화로도 범위를 좁히며 지워졌으면 null이다. 참여자 검사는 구독 시작 때 끝났다. */
    fun message(conversationId: Long, messageId: Long, memberId: Long): MessageView?

    /** 메시지·타이핑 발신자. 없는 회원(탈퇴)이면 null이며 리졸버가 대체 표기로 채운다. 구독 경로 전용이다(DataLoader를 못 쓴다). */
    fun sender(memberId: Long): MemberSummaryView?
}
