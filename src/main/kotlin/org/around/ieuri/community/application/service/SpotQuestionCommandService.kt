package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotQuestionOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.`in`.command.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 질문 작성·삭제·해결 표시(SPEC-0026).
 * - 로그인한 회원이면 장소 반응과 무관하게 질문할 수 있다. 장소 실재 여부만 확인한다.
 */
@Service
class SpotQuestionCommandService(
    private val questionRepositoryPort: SpotQuestionRepositoryPort,
    private val spotLookupPort: SpotLookupPort,
    private val photoKeyVerifier: SpotQaPhotoKeyVerifier,
) : SpotQuestionCommandUseCase {

    @Transactional
    override fun createQuestion(
        command: CreateSpotQuestionCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long {
        // INVALID_INPUT으로 내려고 도메인보다 먼저 판정한다.
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotQuestion.MAX_PHOTOS) throw TooManyPhotosException()

        // 활성 언어에 원본이 없는 장소에는 질문을 붙일 수 없다.
        if (spotLookupPort.findSummary(command.catalogId, language) == null) {
            throw SpotNotFoundInLocaleException()
        }

        // 하나라도 어긋나면 질문 전체를 만들지 않는다 — 부분 생성은 없다.
        val photoKeys = command.photoKeys.map { photoKeyVerifier.verify(it, memberId) }

        val question = SpotQuestion.create(
            catalogId = command.catalogId,
            authorId = memberId,
            body = body,
            photoKeys = photoKeys,
        )
        val saved = questionRepositoryPort.save(question)
        return requireNotNull(saved.id) { "저장된 질문에는 id가 있어야 한다." }
    }

    /** 삭제. 타인의 것을 포함해 딸린 답변·답글이 함께 사라진다. */
    @Transactional
    override fun deleteQuestion(command: DeleteSpotQuestionCommand, memberId: Long) {
        val question = questionRepositoryPort.findById(command.questionId)
            ?: throw QuestionNotFoundException()
        if (!question.ownedBy(memberId)) throw NotQuestionOwnerException()
        questionRepositoryPort.deleteWithDescendants(command.questionId)
    }

    /** 해결 상태 변경. 양방향이며 멱등이다 — 이미 그 상태여도 성공이다. 답변은 영향받지 않는다. */
    @Transactional
    override fun resolveQuestion(command: ResolveSpotQuestionCommand, memberId: Long) {
        val question = questionRepositoryPort.findById(command.questionId)
            ?: throw QuestionNotFoundException()
        if (!question.changeResolvedBy(memberId, command.resolved)) throw NotQuestionOwnerException()
        questionRepositoryPort.save(question)
    }

    private fun requireValidBody(raw: String): String {
        val body = raw.trim()
        if (body.isEmpty()) throw InvalidInputException("질문을 입력해 주세요.")
        if (body.length > SpotQuestion.BODY_MAX_LENGTH) {
            throw InvalidInputException("질문은 ${SpotQuestion.BODY_MAX_LENGTH}자를 넘을 수 없습니다.")
        }
        return body
    }
}
