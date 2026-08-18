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
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerReplyRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 답변·답글 작성과 삭제(SPEC-0026 R4, R7, R10).
 *
 * **작성 자격을 묻지 않는다.** 로그인한 회원이면 그 장소에 어떤 반응을 남겼든 답변·답글을
 * 쓸 수 있다 — 그 장소를 아는 사람과 좋아요를 누른 사람이 같지 않기 때문이다. 싫어했더라도
 * 웨이팅이 얼마였는지는 답할 수 있다.
 *
 * 해결됨으로 표시된 질문에도 답변할 수 있다(AC-4.4) — 해결 표시는 "질문자가 충분히 알게
 * 되었다"는 뜻이지 대화를 닫는 조치가 아니다.
 */
@Service
class SpotAnswerCommandService(
    private val answerRepositoryPort: SpotAnswerRepositoryPort,
    private val replyRepositoryPort: SpotAnswerReplyRepositoryPort,
    private val questionRepositoryPort: SpotQuestionRepositoryPort,
    private val questionCounterPort: SpotQuestionCounterPort,
    private val photoKeyVerifier: SpotQaPhotoKeyVerifier,
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
        return requireNotNull(saved.id) { "저장된 답변에는 id가 있어야 한다." }
    }

    /**
     * 답변 삭제. 딸린 답글도 함께 사라진다(AC-10.4).
     *
     * **숨겨진 답변은 이미 답변 수에서 빠져 있다** — 그것을 지울 때 또 줄이면 수가 실제보다
     * 작아진다. 삭제 전에 세어지고 있었는지를 보고 결정한다(CommentCommandService와 같은 규율).
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
     * 답글 작성. **부모는 답변이어야 한다**(R7).
     *
     * 답글 id를 넘긴 경우 답변 조회가 비어 그것으로 판정된다 — 답글 테이블에 그 id가 실재하는지
     * 확인해 REPLY_TO_REPLY_NOT_ALLOWED와 ANSWER_NOT_FOUND를 가른다. 둘을 뭉뚱그리면
     * 클라이언트가 "잘못된 대상"과 "사라진 대상"을 구분하지 못한다.
     */
    @Transactional
    override fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long {
        val body = requireValidBody(command.body)
        if (command.photoKeys.size > SpotAnswerReply.MAX_PHOTOS) throw TooManyPhotosException()

        val answer = answerRepositoryPort.findById(command.parentAnswerId)
            ?: throw parentNotAnswerException(command.parentAnswerId)

        val photoKeys = command.photoKeys.map { photoKeyVerifier.verify(it, memberId) }

        val reply = SpotAnswerReply.create(
            answerId = command.parentAnswerId,
            catalogId = answer.catalogId,
            authorId = memberId,
            body = body,
            photoKeys = photoKeys,
        )
        val saved = replyRepositoryPort.save(reply)
        return requireNotNull(saved.id) { "저장된 답글에는 id가 있어야 한다." }
    }

    @Transactional
    override fun deleteReply(command: DeleteSpotAnswerReplyCommand, memberId: Long) {
        val reply = replyRepositoryPort.findById(command.replyId) ?: throw ReplyNotFoundException()
        if (!reply.ownedBy(memberId)) throw NotReplyOwnerException()
        replyRepositoryPort.delete(command.replyId)
    }

    /**
     * 답변으로 찾지 못한 부모 id의 정체를 가른다. 답글이면 1단계 위반이고, 아무것도 아니면
     * 사라진 답변이다.
     */
    private fun parentNotAnswerException(parentId: Long): RuntimeException =
        if (replyRepositoryPort.findById(parentId) != null) {
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
