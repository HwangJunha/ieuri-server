package org.around.ieuri.community.application.reply

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.answer.provided.AnswerNotFoundException
import org.around.ieuri.community.application.answer.required.SpotAnswerRepository
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.application.reply.provided.ReplyNotFoundException
import org.around.ieuri.community.application.reply.provided.ReplyToReplyNotAllowedException
import org.around.ieuri.community.application.reply.provided.SpotAnswerReplyCommandUseCase
import org.around.ieuri.community.application.reply.provided.dto.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.reply.required.SpotAnswerReplyRepository
import org.around.ieuri.community.application.shared.UploadedPhotoVerifier
import org.around.ieuri.community.domain.event.SpotAnswerReplyNotificationTriggered
import org.around.ieuri.community.domain.reply.SpotAnswerReply
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 답글 작성·삭제. R2 확인은 첫 쿼리 전에 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotAnswerReplyModifyService(
    private val replyRepository: SpotAnswerReplyRepository,
    private val answerRepository: SpotAnswerRepository,
    private val questionRepository: SpotQuestionRepository,
    private val photoVerifier: UploadedPhotoVerifier,
    private val eventPublisher: CommunityEventPublisherPort,
) : SpotAnswerReplyCommandUseCase {

    override fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long {
        val photoKeys = photoVerifier.parse(command.photoKeys)
        photoVerifier.requireUploaded(photoKeys)

        // 답변과 답글은 id 공간이 달라, 답변이 없을 때만 같은 번호의 답글이 있는지로 오류를 고른다.
        val answer = answerRepository.findById(command.parentAnswerId)
            ?: throw if (replyRepository.exists(command.parentAnswerId)) ReplyToReplyNotAllowedException() else AnswerNotFoundException()
        // 답변 행은 질문이 지워져도 남을 수 있어 질문이 살아 있는지 따로 본다.
        if (!questionRepository.exists(answer.questionId)) throw QuestionNotFoundException()

        val reply = SpotAnswerReply.write(answer, memberId, command.body)
        val photos = AttachedPhotos.of(photoKeys, memberId)

        val replyId = replyRepository.insert(reply, photos)

        // 푸시 알림 트리거. 수신자는 답변 작성자이며 질문 작성자에게는 보내지 않는다.
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

    override fun deleteReply(replyId: Long, memberId: Long) {
        require(replyId > 0) { "replyId는 양수여야 합니다." }
        val reply = replyRepository.findById(replyId) ?: throw ReplyNotFoundException()

        reply.requireAuthor(memberId)

        replyRepository.delete(replyId)
    }
}
