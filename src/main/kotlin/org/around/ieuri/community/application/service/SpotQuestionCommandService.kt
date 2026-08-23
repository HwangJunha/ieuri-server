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
 * 질문 작성·삭제·해결 표시(SPEC-0026 R3, R8, R10).
 *
 * **작성 자격을 묻지 않는다.** 로그인한 회원이면 그 장소에 어떤 반응을 남겼든(좋아요·싫어요·
 * 무반응) 질문할 수 있다 — 질문은 아직 모르는 사람이 하는 것이라, 좋아요를 먼저 요구하면
 * 정작 물어볼 것이 있는 사람이 막힌다.
 *
 * 남는 확인은 **장소가 실재하는가** 하나뿐이다.
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
        // 본문 판정을 도메인보다 먼저 한다 — 도메인의 require는 IllegalArgumentException 하나로
        // 뭉뚱그려지는데, 계약은 INVALID_INPUT에 field를 실어 위치를 알려야 한다.
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotQuestion.MAX_PHOTOS) throw TooManyPhotosException()

        // 없는 장소에는 질문을 붙일 수 없다. 부재가 곧 대상 유효성 판정이다.
        // 기준은 **활성 언어다**(SPEC-0029 R-16) — 이 언어에 원본이 없는 장소도 붙일 수 없다.
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

    /**
     * 삭제. **딸린 답변·답글이 함께 사라진다**(AC-10.3) — 타인이 쓴 답변도 포함이다.
     * 질문이 없어진 자리에 답변만 남으면 맥락 없는 조각이 되기 때문이다.
     */
    @Transactional
    override fun deleteQuestion(command: DeleteSpotQuestionCommand, memberId: Long) {
        val question = questionRepositoryPort.findById(command.questionId)
            ?: throw QuestionNotFoundException()
        if (!question.ownedBy(memberId)) throw NotQuestionOwnerException()
        questionRepositoryPort.deleteWithDescendants(command.questionId)
    }

    /**
     * 해결 상태 변경. **양방향이며 양쪽 다 멱등이다**(R8) — 이미 그 상태여도 성공이다.
     *
     * 되돌리면 목록의 미해결 구간으로 올라간다(정렬이 `resolved ASC`라서다). 답변은
     * 그대로 남는다 — 해결 표시는 답변을 막지도 지우지도 않는다.
     */
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
