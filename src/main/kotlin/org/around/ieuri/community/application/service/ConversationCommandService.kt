package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.application.event.NewMessageStored
import org.around.ieuri.community.application.exception.BlockedException
import org.around.ieuri.community.application.exception.ConversationNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.NotConversationParticipantException
import org.around.ieuri.community.application.exception.PhotoNotConfirmedException
import org.around.ieuri.community.application.exception.SelfConversationException
import org.around.ieuri.community.application.port.`in`.command.ConversationCommandUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.query.TypingEventView
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.event.MessageNotificationTriggered
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 대화 시작·메시지 전송·타이핑 알림(SPEC-0017).
 * - 차단은 대화 시작과 전송 두 지점에서 판정한다 — 대화 성립 후의 차단도 전송을 막는다.
 * - 없는 대화(CONVERSATION_NOT_FOUND)와 참여자가 아닌 대화를 다른 코드로 구분한다.
 */
@Service
class ConversationCommandService(
    private val conversationRepositoryPort: ConversationRepositoryPort,
    private val messageRepositoryPort: MessageRepositoryPort,
    private val memberLookupPort: MemberLookupPort,
    private val communityQueryPort: CommunityQueryPort,
    private val conversationEventPort: ConversationEventPort,
    private val conversationQueryPort: ConversationQueryPort,
    private val spotQaQueryPort: SpotQaQueryPort,
    private val spotLookupPort: SpotLookupPort,
    private val eventPublisher: CommunityEventPublisherPort,
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
) : ConversationCommandUseCase {

    /**
     * 상대 회원을 지목해 대화를 연다(SPEC-0021).
     * - 탈퇴 회원과 없는 회원은 같은 코드(MEMBER_NOT_FOUND)로 다룬다.
     * - 같은 상대와 대화가 이미 있으면 기존 대화 id를 돌려준다.
     */
    @Transactional
    override fun startConversation(command: StartConversationCommand, memberId: Long): Long {
        val counterpartId = command.counterpartMemberId
        // 자기 자신과의 대화는 존재 확인보다 먼저 거부한다.
        if (counterpartId == memberId) throw SelfConversationException()
        if (memberLookupPort.findAuthor(counterpartId) == null) throw MemberNotFoundException()

        requireNotBlocked(memberId, counterpartId)

        val conversation = conversationRepositoryPort.saveIfAbsent(
            Conversation.create(
                memberId = memberId,
                counterpartId = counterpartId,
                now = clockPort.now(),
            ),
        )
        return requireNotNull(conversation.id) { "저장된 대화에는 id가 있어야 한다." }
    }

    /**
     * 인용 스냅샷을 만든다(SPEC-0026). 만들지 못하면 null이며 오류가 아니다 — 메시지는 인용 없이 전송된다.
     * - null: 답변·질문이 사라졌거나, 답변 작성자가 대화 상대가 아니거나, 장소가 [language]로 해소되지 않을 때.
     */
    private fun resolveSpotQaOrigin(
        answerId: Long,
        counterpartId: Long,
        language: ContentLanguage,
    ): SpotQaOrigin? {
        val source = spotQaQueryPort.findAnswerOriginSource(answerId) ?: return null
        if (source.answerAuthorId != counterpartId) return null
        val spot = spotLookupPort.findSummary(source.catalogId, language) ?: return null

        return SpotQaOrigin(
            catalogId = source.catalogId,
            spotTitle = spot.title,
            questionId = source.questionId,
            questionExcerpt = SpotQaOrigin.excerpt(source.questionBody),
            answerId = source.answerId,
            answerExcerpt = SpotQaOrigin.excerpt(source.answerBody),
        )
    }

    @Transactional
    override fun sendMessage(
        command: SendMessageCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long {
        val conversation = requireParticipatingConversation(command.conversationId, memberId)
        // 대화 성립 이후의 차단도 막는다.
        requireNotBlocked(memberId, conversation.counterpartOf(memberId))

        val photoKey = command.photoKey?.let { verifiedPhotoKey(it, memberId) }

        // 인용은 대화 상대의 답변만 가능하다.
        val counterpartId = conversation.counterpartOf(memberId)
        val spotQaOrigin = command.sourceAnswerId?.let {
            resolveSpotQaOrigin(it, counterpartId, language)
        }

        // 본문·사진·인용이 모두 비면 도메인이 거부한다. 상한 초과는 절삭이 아니라 거부다.
        val saved = messageRepositoryPort.save(
            Message.create(
                conversationId = command.conversationId,
                senderId = memberId,
                body = command.body,
                photoKey = photoKey,
                spotQaOrigin = spotQaOrigin,
            ),
        )
        // 대화 목록의 최근 활동순 정렬을 위해 갱신한다.
        conversationRepositoryPort.touch(
            command.conversationId,
            requireNotNull(saved.createdAt) { "저장된 메시지에는 createdAt이 있어야 한다." },
        )
        val messageId = requireNotNull(saved.id) { "저장된 메시지에는 id가 있어야 한다." }

        // 브로커 발행은 커밋 뒤에 한다(NewMessageBroadcastListener) — 커밋 전이면 구독자가 행을 못 읽는다.
        // 본문은 싣지 않고 id만 싣는다(POL-013).
        eventPublisher.publish(NewMessageStored(command.conversationId, messageId))
        // 푸시 알림 트리거(SPEC-0028).
        eventPublisher.publish(
            MessageNotificationTriggered(
                conversationId = command.conversationId,
                messageId = messageId,
                senderId = memberId,
                recipientId = counterpartId,
            ),
        )
        return messageId
    }

    /**
     * 읽음 처리. 읽음 지점은 뒤로 가지 않으므로 반복 호출해도 안전하다. 남은 안 읽은 수를 돌려준다.
     * - upToMessageId가 null이면 마지막 메시지까지 읽는다. 메시지가 없는 대화면 0이다.
     */
    @Transactional
    override fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int {
        val conversation = requireParticipatingConversation(command.conversationId, memberId)

        val upTo = command.upToMessageId
            ?: conversationQueryPort.findLastMessageId(command.conversationId)
            ?: return 0

        conversation.markRead(memberId, upTo)
        conversationRepositoryPort.updateReadState(conversation)

        // 이 질의가 community_conversation을 조인해야 Hibernate가 먼저 flush한다 — 조인을 빼면 갱신 전 값이 나간다.
        return conversationQueryPort
            .countUnreadOf(listOf(command.conversationId), memberId)[command.conversationId]
            ?: 0
    }

    /**
     * 타이핑 알림. 영속화하지 않으며 전달 보장이 없다.
     * - 수신자가 구독 중이 아니거나 브로커가 끊겨도 성공한다. 없는 대화는 CONVERSATION_NOT_FOUND다.
     */
    @Transactional(readOnly = true)
    override fun notifyTyping(command: NotifyTypingCommand, memberId: Long) {
        requireParticipatingConversation(command.conversationId, memberId)

        conversationEventPort.publishTyping(
            TypingEventView(
                conversationId = command.conversationId,
                senderId = memberId,
                emittedAt = clockPort.now(),
            ),
        )
    }

    /**
     * 키의 형식·소유권·확정 여부와 실제 오브젝트 존재를 본다.
     * - 모든 실패는 PhotoNotConfirmedException 하나다 — 남의 키 존재 여부를 드러내지 않는다.
     */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotConfirmedException()
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotConfirmedException()
        if (objectStoragePort.findSize(key.value) == null) throw PhotoNotConfirmedException()
        return key
    }

    /** 존재 확인이 먼저, 참여자 판정이 그다음이다 — 두 실패는 서로 다른 코드다. */
    private fun requireParticipatingConversation(conversationId: Long, memberId: Long): Conversation {
        val conversation = conversationRepositoryPort.findById(conversationId)
            ?: throw ConversationNotFoundException()
        if (!conversation.hasParticipant(memberId)) throw NotConversationParticipantException()
        return conversation
    }

    /** 차단은 양방향이다 — 어느 쪽이 차단했든 대화할 수 없다(POL-041). */
    private fun requireNotBlocked(memberId: Long, counterpartId: Long) {
        if (counterpartId in communityQueryPort.findBlockedMemberIds(memberId)) {
            throw BlockedException()
        }
    }
}
