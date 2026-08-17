package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotEligibleToAskException
import org.around.ieuri.community.application.exception.NotQuestionOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.`in`.command.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.port.out.client.SpotLikeQueryPort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 질문 작성·삭제·해결 표시(SPEC-0026 R3, R8, R10).
 *
 * **작성 자격은 이 계층이 판정한다.** 자격은 다른 애그리게이트(SpotReaction)의 상태이고,
 * 질문 도메인이 그것을 알면 경계가 무너진다. 판정 시점은 화면 진입이 아니라 이 커맨드를
 * 처리하는 순간이며(R5), 클라이언트의 버튼 노출은 UX이지 방어가 아니다.
 *
 * 자격은 **쓰기 경로에만** 있다. 반응이 LIKE에서 벗어나도 이미 작성한 질문은 지우지 않는다(R6) —
 * 그 "하지 않음"을 구현하는 방법은 반응 변경 이벤트를 구독하지 않는 것이다.
 */
@Service
class SpotQuestionCommandService(
    private val questionRepositoryPort: SpotQuestionRepositoryPort,
    private val spotLikeQueryPort: SpotLikeQueryPort,
    private val spotLookupPort: SpotLookupPort,
    private val photoKeyVerifier: SpotQaPhotoKeyVerifier,
) : SpotQuestionCommandUseCase {

    @Transactional
    override fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long): Long {
        // 본문 판정을 도메인보다 먼저 한다 — 도메인의 require는 IllegalArgumentException 하나로
        // 뭉뚱그려지는데, 계약은 INVALID_INPUT에 field를 실어 위치를 알려야 한다.
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotQuestion.MAX_PHOTOS) throw TooManyPhotosException()

        // 장소 존재를 자격보다 먼저 본다 — 없는 장소에 대고 "좋아요하세요"라고 답할 수는 없다.
        if (spotLookupPort.findSummary(command.catalogId) == null) throw SpotNotFoundException()
        if (!spotLikeQueryPort.likedByMember(command.catalogId, memberId)) {
            throw NotEligibleToAskException()
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

    /** 이미 해결됨에 다시 호출해도 성공이다 — 멱등이며 되돌리는 경로는 없다(AC-8.3). */
    @Transactional
    override fun resolveQuestion(command: ResolveSpotQuestionCommand, memberId: Long) {
        val question = questionRepositoryPort.findById(command.questionId)
            ?: throw QuestionNotFoundException()
        if (!question.resolveBy(memberId)) throw NotQuestionOwnerException()
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
