package org.around.ieuri.community.application.qa

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.qa.provided.AnswerNotFoundException
import org.around.ieuri.community.application.qa.provided.SpotAnswerCommandUseCase
import org.around.ieuri.community.application.qa.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.application.qa.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.qa.required.SpotAnswerRepository
import org.around.ieuri.community.application.qa.required.SpotQuestionRepository
import org.around.ieuri.community.application.shared.UploadedPhotoVerifier
import org.around.ieuri.community.domain.event.SpotAnswerNotificationTriggered
import org.around.ieuri.community.domain.qa.SpotAnswer
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 답변 작성·삭제. 해결 표시된 질문에도 답할 수 있다. R2 확인은 첫 쿼리 전에 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotAnswerModifyService(
    private val answerRepository: SpotAnswerRepository,
    private val questionRepository: SpotQuestionRepository,
    private val photoVerifier: UploadedPhotoVerifier,
    private val eventPublisher: CommunityEventPublisherPort,
) : SpotAnswerCommandUseCase {

    override fun createAnswer(command: CreateSpotAnswerCommand, memberId: Long): Long {
        val photoKeys = photoVerifier.parse(command.photoKeys)
        photoVerifier.requireUploaded(photoKeys)

        val question = questionRepository.findById(command.questionId) ?: throw QuestionNotFoundException()

        val answer = SpotAnswer.write(question, memberId, command.body)
        val photos = AttachedPhotos.of(photoKeys, memberId)

        val answerId = answerRepository.insert(answer, photos)
        questionRepository.increaseAnswerCount(command.questionId)

        // 푸시 알림 트리거. 자기 질문에 자기가 답한 경우는 alarm이 거른다.
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

    /** 숨겨진 답변은 이미 답변 수에서 빠져 있어 다시 내리지 않는다. */
    override fun deleteAnswer(answerId: Long, memberId: Long) {
        require(answerId > 0) { "answerId는 양수여야 합니다." }
        val answer = answerRepository.findById(answerId) ?: throw AnswerNotFoundException()

        answer.requireAuthor(memberId)

        if (answerRepository.deleteWithRepliesWasCounted(answerId)) questionRepository.decreaseAnswerCount(answer.questionId)
    }
}
