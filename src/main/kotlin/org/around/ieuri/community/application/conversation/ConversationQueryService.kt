package org.around.ieuri.community.application.conversation

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.community.application.conversation.provided.ConversationQueryUseCase
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
import org.around.ieuri.community.application.conversation.required.ConversationEventPort
import org.around.ieuri.community.application.conversation.required.ConversationMessagesSearch
import org.around.ieuri.community.application.conversation.required.ConversationQueryPort
import org.around.ieuri.community.application.conversation.required.ConversationsSearch
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux

/**
 * 대화·메시지 조회와 타이핑·새 메시지 구독(SPEC-0017).
 * - 없거나 참여자가 아닌 대화는 모두 [ForbiddenException](최상위 errors[])이다 — 뮤테이션의 userErrors[]와 다르다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class ConversationQueryService(
    private val conversationQueryPort: ConversationQueryPort,
    private val conversationEventPort: ConversationEventPort,
    private val policy: CommunityPolicy,
) : ConversationQueryUseCase {

    override fun conversations(
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<ConversationView> = conversationQueryPort.findConversations(
        ConversationsSearch(
            memberId = memberId,
            first = clampPageSize(first, DEFAULT_CONVERSATIONS_PAGE_SIZE),
            after = after,
        ),
    )

    override fun conversationMessages(
        conversationId: Long,
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<MessageView> {
        requireParticipant(conversationId, memberId)
        return conversationQueryPort.findMessages(
            ConversationMessagesSearch(
                conversationId = conversationId,
                memberId = memberId,
                first = clampPageSize(first, DEFAULT_MESSAGES_PAGE_SIZE),
                after = after,
            ),
        )
    }

    /** 구독 시작 시점에 참여자를 검사한다(POL-048). 연결 인증과 별개다. */
    override fun typingIndicator(conversationId: Long, memberId: Long): Flux<TypingEventView> {
        requireParticipant(conversationId, memberId)
        // 자기 타이핑은 걸러낸다 — 이 스트림은 "상대가 입력 중"이다.
        return conversationEventPort.subscribeTyping(conversationId)
            .filter { it.senderId != memberId }
    }

    /** 새 메시지 구독. 브로커는 id만 싣는다(POL-013) — 본문은 호출자가 [message]로 다시 읽는다. */
    override fun newMessageIds(conversationId: Long, memberId: Long): Flux<Long> {
        requireParticipant(conversationId, memberId)
        return conversationEventPort.subscribeNewMessage(conversationId)
    }

    override fun message(conversationId: Long, messageId: Long, memberId: Long): MessageView? = conversationQueryPort.findMessage(conversationId, messageId, memberId)

    /** 읽기 포트로 판정한다. 결과가 없으면 "없거나 볼 수 없다"이다. */
    private fun requireParticipant(conversationId: Long, memberId: Long) {
        conversationQueryPort.findConversation(conversationId, memberId)
            ?: throw ForbiddenException("대화를 볼 수 있는 권한이 없습니다.")
    }

    /** 상한 초과는 오류가 아니라 절삭이다(POL-018). null이면 계약의 기본값이다. */
    private fun clampPageSize(first: Int?, default: Int): Int = (first ?: default).coerceIn(1, policy.maxPageSize)

    private companion object {
        const val DEFAULT_CONVERSATIONS_PAGE_SIZE = 20
        const val DEFAULT_MESSAGES_PAGE_SIZE = 30
    }
}
