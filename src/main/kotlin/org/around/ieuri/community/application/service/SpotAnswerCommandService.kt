package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.exception.AnswerNotFoundException
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotAnswerOwnerException
import org.around.ieuri.community.application.exception.NotReplyOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.ReplyNotFoundException
import org.around.ieuri.community.application.exception.ReplyToReplyNotAllowedException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.`in`.command.SpotAnswerCommandUseCase
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerReplyRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.event.SpotAnswerNotificationTriggered
import org.around.ieuri.community.domain.event.SpotAnswerReplyNotificationTriggered
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 답변·답글 작성과 삭제(SPEC-0026).
 * - 로그인한 회원이면 장소 반응과 무관하게 쓸 수 있다. 해결됨으로 표시된 질문에도 답변할 수 있다.
 */
@Service
class SpotAnswerCommandService(
    private val answerRepositoryPort: SpotAnswerRepositoryPort,
    private val replyRepositoryPort: SpotAnswerReplyRepositoryPort,
    private val questionRepositoryPort: SpotQuestionRepositoryPort,
    private val questionCounterPort: SpotQuestionCounterPort,
    private val photoKeyVerifier: SpotQaPhotoKeyVerifier,
    private val eventPublisher: CommunityEventPublisherPort,
) : SpotAnswerCommandUseCase {

    @Transactional
    override fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long {
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotAnswer.MAX_PHOTOS) throw TooManyPhotosException()

        val question = questionRepositoryPort.findById(command.questionId)
            ?: throw QuestionNotFoundException()

        val photoKeys = command.photoKeys.map { photoKeyVerifier.verify(it, memberId) }

        val answer = SpotAnswer.create(
            questionId = command.questionId,
            catalogId = question.catalogId,
            authorId = memberId,
            body = body,
            photoKeys = photoKeys,
        )
        val saved = answerRepositoryPort.save(answer)
        questionCounterPort.increaseAnswerCount(command.questionId)
        val answerId = requireNotNull(saved.id) { "저장된 답변에는 id가 있어야 한다." }

        // 푸시 알림 트리거(SPEC-0028). 자기 질문에 자기가 답한 경우는 alarm이 거른다.
        eventPublisher.publish(
            SpotAnswerNotificationTriggered(
                spotId = question.catalogId,
                questionId = command.questionId,
                answerId = answerId,
                actorId = memberId,
                recipientId = question.authorId,
            ),
        )
        return answerId
    }

    /**
     * 답변 삭제. 딸린 답글도 함께 사라진다.
     * - 숨겨진 답변은 이미 답변 수에서 빠져 있으므로 수를 줄이지 않는다.
     */
    @Transactional
    override fun deleteAnswer(command: DeleteSpotAnswerCommand, memberId: Long) {
        val answer = answerRepositoryPort.findById(command.answerId)
            ?: throw AnswerNotFoundException()
        if (!answer.ownedBy(memberId)) throw NotAnswerOwnerException()
        val countedBeforeDelete = !answer.hidden
        answerRepositoryPort.deleteWithDescendants(command.answerId)
        if (countedBeforeDelete) questionCounterPort.decreaseAnswerCount(answer.questionId)
    }

    /**
     * 답글 작성. 부모는 답변이어야 한다.
     * - 부모가 답글이면 REPLY_TO_REPLY_NOT_ALLOWED, 아무것도 아니면 ANSWER_NOT_FOUND다.
     */
    @Transactional
    override fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long {
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotAnswerReply.MAX_PHOTOS) throw TooManyPhotosException()

        val answer = answerRepositoryPort.findById(command.parentAnswerId)
            ?: throw parentNotAnswerException(command.parentAnswerId)
        // 부모 질문이 살아 있어야 한다 — 답변 행은 질문이 지워져도 남는다.
        questionRepositoryPort.findById(answer.questionId) ?: throw QuestionNotFoundException()

        val photoKeys = command.photoKeys.map { photoKeyVerifier.verify(it, memberId) }

        val reply = SpotAnswerReply.create(
            answerId = command.parentAnswerId,
            catalogId = answer.catalogId,
            authorId = memberId,
            body = body,
            photoKeys = photoKeys,
        )
        val saved = replyRepositoryPort.save(reply)
        val replyId = requireNotNull(saved.id) { "저장된 답글에는 id가 있어야 한다." }

        // 푸시 알림 트리거(SPEC-0028). 수신자는 답변 작성자이며 질문 작성자에게는 보내지 않는다.
        eventPublisher.publish(
            SpotAnswerReplyNotificationTriggered(
                spotId = answer.catalogId,
                questionId = answer.questionId,
                replyId = replyId,
                actorId = memberId,
                recipientId = answer.authorId,
            ),
        )
        return replyId
    }

    @Transactional
    override fun deleteReply(command: DeleteSpotAnswerReplyCommand, memberId: Long) {
        val reply = replyRepositoryPort.findById(command.replyId) ?: throw ReplyNotFoundException()
        if (!reply.ownedBy(memberId)) throw NotReplyOwnerException()
        replyRepositoryPort.delete(command.replyId)
    }

    /** 답변으로 찾지 못한 부모 id가 답글이면 1단계 위반, 아니면 사라진 답변이다. */
    private fun parentNotAnswerException(parentId: Long): RuntimeException = if (replyRepositoryPort.findById(parentId) != null) {
        ReplyToReplyNotAllowedException()
    } else {
        AnswerNotFoundException()
    }

    private fun requireValidBody(raw: String): String {
        val body = raw.trim()
        if (body.isEmpty()) throw InvalidInputException("내용을 입력해 주세요.")
        if (body.length > SpotAnswer.BODY_MAX_LENGTH) {
            throw InvalidInputException("내용은 ${SpotAnswer.BODY_MAX_LENGTH}자를 넘을 수 없습니다.")
        }
        return body
    }
}
