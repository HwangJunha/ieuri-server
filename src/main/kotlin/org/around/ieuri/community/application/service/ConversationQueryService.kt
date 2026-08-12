package org.around.ieuri.community.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.ConversationQueryUseCase
import org.around.ieuri.community.application.port.`in`.query.ConversationSubscription
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.port.out.query.ConversationMessagesSearch
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationsSearch
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.TypingEventView
import org.springframework.stereotype.Service

/**
 * 대화·메시지 조회와 타이핑 구독(SPEC-0017 R8·R10).
 *
 * **참여자가 아니면 [ForbiddenException]이다** — 뮤테이션의 NOT_CONVERSATION_PARTICIPANT와
 * 갈리는 지점이다(AC8-3·10-3). 조회는 볼 자격 자체가 없어 부분 성공이 성립하지 않으므로
 * 최상위 errors[]로 나가고, 뮤테이션은 요청이 정상 처리된 뒤 도메인 규칙에 걸린 것이라
 * userErrors[]로 나간다(spec.md "권한 실패의 표면화 위치").
 *
 * 없는 대화도 [ForbiddenException]으로 답한다 — 조회 경로에서는 존재 여부를 구분해 줄 이유가
 * 없고, 구분하면 대화 id 존재 확인 수단이 된다. 뮤테이션 쪽은 클라이언트가 "사라진 대화"와
 * "볼 수 없는 대화"를 나눠 처리해야 해서 구분한다(AC9-5).
 */
@Service
class ConversationQueryService(
    private val conversationQueryPort: ConversationQueryPort,
    private val conversationEventPort: ConversationEventPort,
    private val policy: CommunityPolicy,
) : ConversationQueryUseCase {

    override fun conversations(
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<ConversationView> =
        conversationQueryPort.findConversations(
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

    /**
     * **구독 시작 시점에 참여자를 검사한다**(POL-048) — 연결이 살아 있다는 것이 특정 대화를
     * 볼 자격의 증명은 아니다. 연결 수립 시의 인증과 별개 판정이다.
     */
    override fun typingIndicator(
        conversationId: Long,
        memberId: Long,
    ): ConversationSubscription<TypingEventView> {
        requireParticipant(conversationId, memberId)
        return conversationEventPort.subscribeTyping(conversationId)
    }

    /**
     * 새 메시지 구독. 이벤트에는 **id만 실려 오므로 DB에서 다시 읽어** 내려준다 —
     * 본문을 브로커에 싣지 않기 때문이고(POL-013), 그래서 값의 원본이 항상 DB다.
     *
     * 읽는 사이에 보관 만료·탈퇴로 사라진 메시지는 **조용히 건너뛴다** — 구독자에게 빈
     * 말풍선을 내려보내는 것보다 낫다.
     */
    override fun newMessage(
        conversationId: Long,
        memberId: Long,
    ): ConversationSubscription<MessageView> {
        requireParticipant(conversationId, memberId)
        return MappedSubscription(conversationEventPort.subscribeNewMessage(conversationId)) {
            conversationQueryPort.findMessage(it, memberId)
        }
    }

    /**
     * **읽기 포트로 판정한다** — 애그리게이트를 복원하면 참여자 두 명을 알자고 도메인 객체를
     * 만들게 되고, 읽기 서비스가 쓰기 포트에 물려 CQRS 레일이 무너진다.
     *
     * 조회는 참여자 조건을 질의에 담으므로, 결과가 없다는 것이 곧 "없거나 볼 수 없다"이다.
     */
    private fun requireParticipant(conversationId: Long, memberId: Long) {
        conversationQueryPort.findConversation(conversationId, memberId)
            ?: throw ForbiddenException("대화를 볼 수 있는 권한이 없습니다.")
    }

    /** 상한 초과는 오류가 아니라 절삭이다(POL-018). null이면 계약의 기본값이다. */
    private fun clampPageSize(first: Int?, default: Int): Int =
        (first ?: default).coerceIn(1, policy.maxPageSize)

    private companion object {
        const val DEFAULT_CONVERSATIONS_PAGE_SIZE = 20
        const val DEFAULT_MESSAGES_PAGE_SIZE = 30
    }
}

/**
 * 구독 이벤트를 다른 값으로 옮기는 래퍼. 새 메시지 구독이 **id를 받아 DB의 메시지로** 바꾸는
 * 데 쓴다.
 *
 * [transform]이 null을 돌려주면 그 이벤트를 흘려보낸다 — 읽는 사이에 사라진 메시지에
 * 빈 말풍선을 내려보내지 않기 위함이다.
 */
private class MappedSubscription<S, T>(
    private val source: ConversationSubscription<S>,
    private val transform: (S) -> T?,
) : ConversationSubscription<T> {

    override fun onEvent(listener: (T) -> Unit) {
        source.onEvent { raw -> transform(raw)?.let(listener) }
    }

    override fun cancel() = source.cancel()
}
