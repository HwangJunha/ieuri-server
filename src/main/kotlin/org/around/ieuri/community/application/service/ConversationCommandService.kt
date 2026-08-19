package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
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
import org.around.ieuri.community.application.event.NewMessageStored
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.query.TypingEventView
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.event.MessageNotificationTriggered
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 대화 시작·메시지 전송·타이핑 알림(SPEC-0017 R7·R9·R10·R11).
 *
 * **차단을 두 지점에서 판정한다**(R11). 대화 시작에서 한 번, 전송에서 다시 한 번이다 —
 * 대화 성립 이후에 차단이 발생해도 전송 시점에 다시 보므로 기존 대화가 차단을 우회하지
 * 못한다(AC11-4). 생성 시점에만 검사하면 차단이 소급되지 않는다.
 *
 * **없는 대화와 권한 없는 대화를 구분한다**(AC9-5·10-5). 존재 확인이 먼저이고 참여자 판정이
 * 그다음이다 — 순서를 뒤집으면 존재하지 않는 대화가 권한 오류로 보고된다. 이 순서는 대화
 * 식별자의 존재 여부를 노출하지만, 대화 id는 추측 가능한 값이 아니라 열거 공격의 대상이 아니다.
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
     * 상대 회원을 지목해 대화를 연다(SPEC-0021 R2).
     *
     * **탈퇴 회원과 없는 회원을 같은 코드로 다룬다**(AC2.6). 탈퇴는 회원 행 삭제라 조회가
     * 그대로 null이 되며, 구분해 알려 주면 상대가 탈퇴했다는 사실이 노출된다.
     */
    @Transactional
    override fun startConversation(command: StartConversationCommand, memberId: Long): Long {
        val counterpartId = command.counterpartMemberId
        // 자기 자신과의 대화는 만들지 않는다(AC2.3). 존재 확인보다 먼저 본다 —
        // 자기 id는 이미 인증으로 확인된 값이라 조회가 필요 없다.
        if (counterpartId == memberId) throw SelfConversationException()
        if (memberLookupPort.findAuthor(counterpartId) == null) throw MemberNotFoundException()

        requireNotBlocked(memberId, counterpartId)

        // 같은 상대와 대화가 이미 있으면 새로 만들지 않고 기존 것을 돌려준다(AC7-2).
        // 경합 시 삽입 실패를 조회로 흡수하는 것은 어댑터의 몫이다.
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
     * 인용 스냅샷을 만든다(SPEC-0026 R14). **만들지 못하면 null이고 그것은 오류가 아니다** —
     * 메시지는 정상적으로 전송되고 인용만 비어 있다(AC-13.4).
     *
     * null이 되는 경우는 셋이다: 답변이 이미 삭제됐거나, 질문이 사라졌거나, 그 답변의
     * 작성자가 지금 대화 중인 상대가 아닌 경우다. 마지막 조건은 **다른 사람의 답변을
     * 인용으로 붙여 맥락을 위조하는 것**을 막는다.
     *
     * 장소명은 tourism이 소유한 값이라 자기 포트로 읽는다. 장소가 사라졌으면 스냅샷을
     * 만들지 않는다 — 이름 없는 인용 카드는 클라이언트가 그릴 수 없다.
     */
    private fun resolveSpotQaOrigin(answerId: Long, counterpartId: Long): SpotQaOrigin? {
        val source = spotQaQueryPort.findAnswerOriginSource(answerId) ?: return null
        if (source.answerAuthorId != counterpartId) return null
        val spot = spotLookupPort.findSummary(source.catalogId) ?: return null

        return SpotQaOrigin(
            catalogId = source.catalogId,
            spotTitle = spot.title,
            questionId = source.questionId,
            // 절삭은 값 객체가 소유한다 — 조회가 미리 자르면 그 규칙이 두 곳에 생긴다.
            questionExcerpt = SpotQaOrigin.excerpt(source.questionBody),
            answerId = source.answerId,
            answerExcerpt = SpotQaOrigin.excerpt(source.answerBody),
        )
    }

    @Transactional
    override fun sendMessage(command: SendMessageCommand, memberId: Long): Long {
        val conversation = requireParticipatingConversation(command.conversationId, memberId)
        // **대화 성립 이후의 차단도 막는다**(AC11-4). 여기서 다시 보지 않으면 이미 만들어진
        // 대화가 차단을 우회하는 통로가 된다.
        requireNotBlocked(memberId, conversation.counterpartOf(memberId))

        // 사진은 글과 **같은 층위 구분**을 따른다 — 소유권·확정 여부는 도메인이 키 문자열로
        // 보고, 실제 업로드 여부만 여기서 저장소에 묻는다.
        val photoKey = command.photoKey?.let { verifiedPhotoKey(it, memberId) }

        // 인용은 **대화 상대의 답변만** 가능하다 — 남의 답변을 붙여 맥락을 위조할 수 없다.
        val counterpartId = conversation.counterpartOf(memberId)
        val spotQaOrigin = command.sourceAnswerId?.let { resolveSpotQaOrigin(it, counterpartId) }

        // 본문·사진·인용이 모두 비면 도메인이 거부한다. 상한을 넘어도 잘라 저장하지 않는다.
        val saved = messageRepositoryPort.save(
            Message.create(
                conversationId = command.conversationId,
                senderId = memberId,
                body = command.body,
                photoKey = photoKey,
                spotQaOrigin = spotQaOrigin,
            ),
        )
        // 대화 목록의 정렬이 최근 활동순이라 함께 갱신한다(AC9-1).
        conversationRepositoryPort.touch(
            command.conversationId,
            requireNotNull(saved.createdAt) { "저장된 메시지에는 createdAt이 있어야 한다." },
        )
        val messageId = requireNotNull(saved.id) { "저장된 메시지에는 id가 있어야 한다." }

        // **커밋된 뒤에 알린다**(NewMessageBroadcastListener). 여기서 브로커로 바로 발행하면
        // 구독자가 그 id로 DB를 읽는 시점에 아직 커밋 전이라 행이 보이지 않고, 이벤트가 조용히
        // 버려진다 — 커밋과 조회의 경합이라 유실이 무작위로 나타난다.
        // id만 싣는다: 본문을 브로커에 흘리지 않기 위함이다(POL-013).
        eventPublisher.publish(NewMessageStored(command.conversationId, messageId))
        // 푸시 알림 트리거(SPEC-0028 R5). 실시간 구독과 목적이 달라 이벤트를 나눈다 —
        // 구독은 대화를 보고 있는 사람에게, 푸시는 앱이 꺼진 사람에게 간다.
        eventPublisher.publish(
            MessageNotificationTriggered(
                conversationId = command.conversationId,
                messageId = messageId,
                senderId = memberId,
                recipientId = counterpartId,
                body = command.body,
            ),
        )
        return messageId
    }

    /**
     * 읽음 처리. **읽음 지점은 뒤로 가지 않으므로**(도메인이 판정) 같은 요청을 여러 번 보내도
     * 안전하다 — 재시도·중복 호출이 흔한 경로다.
     *
     * upToMessageId가 null이면 마지막 메시지까지 읽은 것으로 본다. 메시지가 하나도 없는
     * 대화면 읽을 것이 없으므로 그대로 0을 돌려준다.
     */
    @Transactional
    override fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int {
        val conversation = requireParticipatingConversation(command.conversationId, memberId)

        val upTo = command.upToMessageId
            ?: conversationQueryPort.findLastMessageId(command.conversationId)
            ?: return 0

        conversation.markRead(memberId, upTo)
        conversationRepositoryPort.updateReadState(conversation)

        // 아직 커밋 전이지만 이 질의가 community_conversation을 조인하므로 Hibernate가
        // 먼저 flush한다 — 조인을 빼면 갱신 전 값이 조용히 나간다.
        return conversationQueryPort
            .countUnreadOf(listOf(command.conversationId), memberId)[command.conversationId]
            ?: 0
    }

    /**
     * **수신자가 구독 중이 아니어도 성공한다**(AC10-2) — 받을 사람이 없는 것은 실패가 아니다.
     * 없는 대화는 여전히 CONVERSATION_NOT_FOUND로 구분한다(AC10-5).
     *
     * 영속화하지 않으며 전달 보장이 없다. 브로커가 끊겨도 예외가 올라오지 않는다(AC14-2).
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
     * 키의 형식·소유권·확정 여부를 판정하고 실제 오브젝트가 있는지까지 본다.
     * 실패를 한 코드로 뭉뚱그리는 이유는 남의 키가 존재하는지 캐낼 수 있어서다.
     */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotConfirmedException()
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotConfirmedException()
        if (objectStoragePort.findSize(key.value) == null) throw PhotoNotConfirmedException()
        return key
    }

    /**
     * 존재 확인이 먼저, 참여자 판정이 그다음이다. 두 실패는 **서로 다른 코드**다 —
     * 뭉뚱그리면 클라이언트가 "사라진 대화"와 "볼 수 없는 대화"를 구분하지 못한다.
     */
    private fun requireParticipatingConversation(conversationId: Long, memberId: Long): Conversation {
        val conversation = conversationRepositoryPort.findById(conversationId)
            ?: throw ConversationNotFoundException()
        if (!conversation.hasParticipant(memberId)) throw NotConversationParticipantException()
        return conversation
    }

    /** 차단은 **양방향이다** — 내가 차단했든 상대가 차단했든 대화할 수 없다(POL-041). */
    private fun requireNotBlocked(memberId: Long, counterpartId: Long) {
        if (counterpartId in communityQueryPort.findBlockedMemberIds(memberId)) {
            throw BlockedException()
        }
    }
}
