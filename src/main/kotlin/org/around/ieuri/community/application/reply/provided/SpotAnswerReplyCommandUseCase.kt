package org.around.ieuri.community.application.reply.provided

import org.around.ieuri.community.application.reply.provided.dto.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.domain.shared.CommunityUserException

/** 답글 쓰기. 답글은 어떤 수에도 잡히지 않는다. */
interface SpotAnswerReplyCommandUseCase {
    /** parentAnswerId가 답변이 아니라 답글을 가리키면 REPLY_TO_REPLY_NOT_ALLOWED. */
    fun createReply(command: CreateSpotAnswerReplyCommand, memberId: Long): Long

    fun deleteReply(replyId: Long, memberId: Long)
}

class ReplyNotFoundException(message: String = "해당 답글을 찾을 수 없습니다.") : CommunityUserException("REPLY_NOT_FOUND", message)

class ReplyToReplyNotAllowedException(message: String = "답글에는 답글을 달 수 없습니다.") : CommunityUserException("REPLY_TO_REPLY_NOT_ALLOWED", message)
