package org.around.ieuri.community.application.question

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.photo.provided.UploadedPhotoUseCase
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.application.question.provided.exception.AnswerNotFoundException
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.provided.exception.ReplyNotFoundException
import org.around.ieuri.community.application.question.provided.exception.ReplyToReplyNotAllowedException
import org.around.ieuri.community.application.question.required.SpotQuestionEventPublisherPort
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.application.shared.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.shared.required.SpotLookupPort
import org.around.ieuri.community.domain.photo.AttachedPhotos
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.question.event.SpotAnswerWritten
import org.around.ieuri.community.domain.question.event.SpotReplyWritten
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 질문·답변·답글 쓰기. 답변·답글은 루트(질문)를 통해 만들고 지운다. 외부 호출(장소 조회·R2)은 첫 쿼리 전에 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotQuestionModifyService(
    private val questionRepository: SpotQuestionRepository,
    private val spotLookupPort: SpotLookupPort,
    private val uploadedPhotos: UploadedPhotoUseCase,
    private val eventPublisher: SpotQuestionEventPublisherPort,
) : SpotQuestionCommandUseCase {

    override fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long, language: ContentLanguage): Long {
        if (spotLookupPort.findSummary(command.catalogId, language) == null) throw SpotNotFoundInLocaleException()
        val question = SpotQuestion.write(command.catalogId, memberId, command.body)
        val photos = AttachedPhotos.of(uploadedPhotos.parse(command.photoKeys), memberId)
        uploadedPhotos.requireUploaded(photos.keys)

        return questionRepository.insert(question, photos)
    }

    override fun deleteQuestion(questionId: Long, memberId: Long) {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
        val question = questionRepository.findById(questionId) ?: throw QuestionNotFoundException()

        question.requireAuthor(memberId)

        questionRepository.deleteWithDescendants(questionId)
    }

    override fun resolveQuestion(questionId: Long, resolved: Boolean, memberId: Long) {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
        val question = questionRepository.findById(questionId) ?: throw QuestionNotFoundException()

        val changed = question.resolve(memberId, resolved)

        questionRepository.changeResolved(questionId, changed.resolved)
    }

    override fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long {
        // 개수·소유·확정을 먼저 판정하고 R2 확인은 그 뒤에 한다 — 남의 키 존재를 응답으로 드러내지 않는다.
        val photos = AttachedPhotos.of(uploadedPhotos.parse(command.photoKeys), memberId)
        uploadedPhotos.requireUploaded(photos.keys)

        val question = questionRepository.findById(command.questionId) ?: throw QuestionNotFoundException()

        val answer = question.answer(memberId, command.body)

        val answerId = questionRepository.insertAnswer(answer, photos)
        questionRepository.increaseAnswerCount(command.questionId)

        // 푸시 알림 트리거. 자기 질문에 자기가 답한 경우는 alarm이 거른다.
        eventPublisher.publish(
            SpotAnswerWritten(
                spotId = question.catalogId,
                questionId = command.questionId,
                answerId = answerId,
                actorId = memberId,
                recipientId = question.authorId,
            ),
        )
        return answerId
    }

    /** 숨겨진 답변은 이미 답변 수에서 빠져 있어 다시 내리지 않는다. */
    override fun deleteAnswer(answerId: Long, memberId: Long) {
        require(answerId > 0) { "answerId는 양수여야 합니다." }
        val answer = questionRepository.findAnswer(answerId) ?: throw AnswerNotFoundException()
        val question = questionRepository.findById(answer.questionId) ?: throw AnswerNotFoundException()

        question.removeAnswer(answer, memberId)

        if (questionRepository.deleteAnswerWasCounted(answerId)) questionRepository.decreaseAnswerCount(answer.questionId)
    }

    override fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long {
        // 개수·소유·확정을 먼저 판정하고 R2 확인은 그 뒤에 한다 — 남의 키 존재를 응답으로 드러내지 않는다.
        val photos = AttachedPhotos.of(uploadedPhotos.parse(command.photoKeys), memberId)
        uploadedPhotos.requireUploaded(photos.keys)

        // 답변과 답글은 id 공간이 달라, 답변이 없을 때만 같은 번호의 답글이 있는지로 오류를 고른다.
        val answer = questionRepository.findAnswer(command.parentAnswerId)
            ?: throw if (questionRepository.replyExists(command.parentAnswerId)) ReplyToReplyNotAllowedException() else AnswerNotFoundException()
        val question = questionRepository.findById(answer.questionId) ?: throw QuestionNotFoundException()

        val reply = question.reply(answer, memberId, command.body)

        val replyId = questionRepository.insertReply(reply, photos)

        // 푸시 알림 트리거. 수신자는 답변 작성자이며 질문 작성자에게는 보내지 않는다.
        eventPublisher.publish(
            SpotReplyWritten(
                spotId = question.catalogId,
                questionId = answer.questionId,
                replyId = replyId,
                actorId = memberId,
                recipientId = answer.authorId,
            ),
        )
        return replyId
    }

    override fun deleteReply(replyId: Long, memberId: Long) {
        require(replyId > 0) { "replyId는 양수여야 합니다." }
        val reply = questionRepository.findReply(replyId) ?: throw ReplyNotFoundException()
        val question = questionRepository.findByAnswerId(reply.answerId) ?: throw ReplyNotFoundException()

        question.removeReply(reply, memberId)

        questionRepository.deleteReply(replyId)
    }

    /**
     * 답글 → 답변 → 질문 순으로 하나씩, 루트(질문)의 탈퇴 메서드를 거쳐 삭제 표시한다. 수에 잡혀 있던 답변만 답변 수를 내린다.
     * - 순서 주의: 자식은 루트를 읽어 판정하므로 질문보다 먼저 처리한다. 루트가 이미 없으면 자식은 읽기에서 걸러지므로 건너뛴다.
     */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int {
        var erased = 0
        questionRepository.findReplyIdsByAuthor(memberId).forEach { replyId ->
            val reply = questionRepository.findReply(replyId) ?: return@forEach
            val question = questionRepository.findByAnswerId(reply.answerId) ?: return@forEach
            val withdrawn = question.withdrawReply(reply, memberId, deletedAt)
            if (questionRepository.markReplyDeleted(replyId, requireNotNull(withdrawn.deletedAt))) erased++
        }
        questionRepository.findAnswerIdsByAuthor(memberId).forEach { answerId ->
            val answer = questionRepository.findAnswer(answerId) ?: return@forEach
            val question = questionRepository.findById(answer.questionId) ?: return@forEach
            val withdrawn = question.withdrawAnswer(answer, memberId, deletedAt)
            if (questionRepository.markAnswerDeletedWasCounted(answerId, requireNotNull(withdrawn.deletedAt))) {
                questionRepository.decreaseAnswerCount(answer.questionId)
            }
            erased++
        }
        questionRepository.findIdsByAuthor(memberId).forEach { questionId ->
            val question = questionRepository.findById(questionId) ?: return@forEach
            val withdrawn = question.withdraw(memberId, deletedAt)
            if (questionRepository.markDeleted(questionId, requireNotNull(withdrawn.deletedAt))) erased++
        }
        return erased
    }
}
