package org.around.ieuri.community.application.conversation.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
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
     * 새 메시지 구독(R9). 참여자 검사는 타이핑과 같고, 본문은 이벤트마다 DB에서 읽는다.
     * - 전달 보장이 없다 — 끊긴 동안의 메시지는 재연결 후 [conversationMessages]로 메꾼다.
     */
    fun newMessage(conversationId: Long, memberId: Long): Flux<MessageView>
}
