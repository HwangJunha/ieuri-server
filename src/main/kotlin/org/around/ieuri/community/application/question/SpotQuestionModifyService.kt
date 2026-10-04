package org.around.ieuri.community.application.question

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.application.shared.UploadedPhotoVerifier
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 질문 작성·삭제·해결 표시. 외부 호출(장소 조회·R2)은 첫 쿼리 전에 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotQuestionModifyService(
    private val questionRepository: SpotQuestionRepository,
    private val spotLookupPort: SpotLookupPort,
    private val photoVerifier: UploadedPhotoVerifier,
) : SpotQuestionCommandUseCase {

    override fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long, language: ContentLanguage): Long {
        if (spotLookupPort.findSummary(command.catalogId, language) == null) throw SpotNotFoundInLocaleException()
        val question = SpotQuestion.write(command.catalogId, memberId, command.body)
        val photos = AttachedPhotos.of(photoVerifier.parse(command.photoKeys), memberId)
        photoVerifier.requireUploaded(photos.keys)

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
}
