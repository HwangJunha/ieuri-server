package org.around.ieuri.community.application.conversation

import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.conversation.provided.ConversationCommandUseCase
import org.around.ieuri.community.application.conversation.provided.ExpiredMessageDeleteUseCase
import org.around.ieuri.community.application.conversation.provided.dto.ExpiredMessages
import org.around.ieuri.community.application.conversation.provided.dto.MarkConversationReadCommand
import org.around.ieuri.community.application.conversation.provided.dto.NotifyTypingCommand
import org.around.ieuri.community.application.conversation.provided.dto.SendMessageCommand
import org.around.ieuri.community.application.conversation.provided.dto.StartConversationCommand
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
import org.around.ieuri.community.application.conversation.provided.exception.BlockedException
import org.around.ieuri.community.application.conversation.provided.exception.ConversationNotFoundException
import org.around.ieuri.community.application.conversation.provided.exception.PhotoNotConfirmedException
import org.around.ieuri.community.application.conversation.required.ConversationEventPort
import org.around.ieuri.community.application.conversation.required.ConversationEventPublisherPort
import org.around.ieuri.community.application.conversation.required.ConversationRepository
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.lookup.provided.SpotLookupUseCase
import org.around.ieuri.community.application.photo.provided.UploadedPhotoUseCase
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.shared.exception.MemberNotFoundException
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.SpotQaOrigin
import org.around.ieuri.community.domain.conversation.event.MessageSent
import org.around.ieuri.community.domain.photo.PhotoObjectKey
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 대화 시작·메시지 전송·읽음·타이핑 알림. 메시지는 루트(대화)를 통해 만든다.
 * - 차단은 대화 시작과 전송 두 지점에서 판정한다 — 대화 성립 후의 차단도 전송을 막는다. 차단은 양방향이다(POL-041).
 * - 외부 호출(회원 조회·R2)은 첫 쿼리 전에 한다. 없는 대화와 참여자가 아닌 대화는 다른 코드다.
 */
@Service
@Transactional
class ConversationModifyService(
    private val conversationRepository: ConversationRepository,
    private val blockQueryUseCase: BlockQueryUseCase,
    private val questionQueryUseCase: SpotQuestionQueryUseCase,
    private val memberLookup: MemberLookupUseCase,
    private val spotLookup: SpotLookupUseCase,
    private val uploadedPhotos: UploadedPhotoUseCase,
    private val conversationEventPort: ConversationEventPort,
    private val eventPublisher: ConversationEventPublisherPort,
    private val clock: Clock,
) : ConversationCommandUseCase,
    ExpiredMessageDeleteUseCase {

    override fun startConversation(command: StartConversationCommand, memberId: Long): Long {
        // 자기 자신과의 대화는 존재 확인보다 먼저 거부한다.
        val conversation = Conversation.start(memberId, command.counterpartMemberId)
        if (memberLookup.member(command.counterpartMemberId) == null) throw MemberNotFoundException()

        requireNotBlocked(memberId, command.counterpartMemberId)

        return conversationRepository.insertIfAbsent(conversation)
    }

    override fun sendMessage(command: SendMessageCommand, memberId: Long, language: ContentLanguage): Long {
        val photoKey = command.photoKey?.let { verifiedPhotoKey(it, memberId) }

        val conversation = requireConversation(command.conversationId)
        val counterpartId = conversation.counterpartOf(memberId)
        requireNotBlocked(memberId, counterpartId)
        // 인용은 대화 상대의 답변만 가능하다.
        val spotQaOrigin = command.sourceAnswerId?.let { resolveSpotQaOrigin(it, counterpartId, language) }

        val message = conversation.send(memberId, command.body, photoKey, spotQaOrigin)

        val saved = conversationRepository.insertMessage(message)
        conversationRepository.advanceLastMessageAt(command.conversationId, requireNotNull(saved.createdAt))
        val messageId = requireNotNull(saved.id)

        // 브로커 발행은 커밋 뒤 리스너가 한다. 본문은 싣지 않고 id만 싣는다(POL-013).
        eventPublisher.publish(NewMessageStored(command.conversationId, messageId))
        eventPublisher.publish(
            MessageSent(
                conversationId = command.conversationId,
                messageId = messageId,
                senderId = memberId,
                recipientId = counterpartId,
            ),
        )
        return messageId
    }

    /** upToMessageId가 null이면 마지막 메시지까지 읽는다. 메시지가 없는 대화면 0이다. */
    override fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int {
        val conversation = requireConversation(command.conversationId)
        conversation.requireParticipant(memberId)
        val upTo = command.upToMessageId
            ?: conversationRepository.lastMessageId(command.conversationId)
            ?: return 0

        val read = conversation.markRead(memberId, upTo)

        conversationRepository.advanceReadPoints(read)
        return conversationRepository.countUnread(command.conversationId, memberId, read.lastReadMessageIdOf(memberId))
    }

    /** 수신자가 구독 중이 아니거나 브로커가 끊겨도 성공한다. */
    override fun notifyTyping(command: NotifyTypingCommand, memberId: Long) {
        requireConversation(command.conversationId).requireParticipant(memberId)

        conversationEventPort.publishTyping(TypingEventView(conversationId = command.conversationId, senderId = memberId, emittedAt = Instant.now(clock)))
    }

    override fun deleteExpiredMessages(limit: Int): ExpiredMessages {
        require(limit > 0) { "limit은 양수여야 합니다." }
        return conversationRepository.deleteMessagesCreatedBefore(Message.retentionCutoff(Instant.now(clock)), limit)
    }

    /** 존재 확인이 먼저, 참여자 판정이 그다음이다 — 두 실패는 서로 다른 코드다. */
    private fun requireConversation(conversationId: Long): Conversation = conversationRepository.findById(conversationId) ?: throw ConversationNotFoundException()

    private fun requireNotBlocked(memberId: Long, counterpartId: Long) {
        if (blockQueryUseCase.isBlockedBetween(memberId, counterpartId)) throw BlockedException()
    }

    /** 형식·소유·확정·실제 존재를 본다. 모든 실패는 PHOTO_NOT_CONFIRMED 하나다 — 남의 키 존재 여부를 드러내지 않는다. */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotConfirmedException()
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotConfirmedException()
        if (!uploadedPhotos.isUploaded(key)) throw PhotoNotConfirmedException()
        return key
    }

    /** 만들지 못하면 null이며 오류가 아니다: 답변·질문이 사라졌거나, 답변 작성자가 상대가 아니거나, 장소가 [language]로 해소되지 않을 때. */
    private fun resolveSpotQaOrigin(answerId: Long, counterpartId: Long, language: ContentLanguage): SpotQaOrigin? {
        val origin = questionQueryUseCase.answerOrigin(answerId) ?: return null
        if (origin.answerAuthorId != counterpartId) return null
        val spot = spotLookup.spot(origin.catalogId, language) ?: return null

        return SpotQaOrigin(
            catalogId = origin.catalogId,
            spotTitle = spot.title,
            questionId = origin.questionId,
            questionExcerpt = SpotQaOrigin.excerpt(origin.questionBody),
            answerId = answerId,
            answerExcerpt = SpotQaOrigin.excerpt(origin.answerBody),
        )
    }

    /** 대화마다 루트가 참여자를 확인한 뒤 그 대화의 보낸 메시지를 삭제 표시한다. 대화는 남긴다. 메시지 한 건 단위의 규칙은 없다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int = conversationRepository.findIdsByParticipant(memberId).sumOf { conversationId ->
        val conversation = conversationRepository.findById(conversationId) ?: return@sumOf 0
        conversation.requireParticipant(memberId)
        conversationRepository.markMessagesDeletedBySender(conversationId, memberId, deletedAt)
    }
}
