package org.around.ieuri.community.application.reply.required

import org.around.ieuri.community.domain.reply.SpotAnswerReply
import org.around.ieuri.community.domain.shared.AttachedPhotos

interface SpotAnswerReplyRepository {
    /** 삭제되지 않은 답글 한 행만 읽는다. 없으면 null. */
    fun findById(replyId: Long): SpotAnswerReply?

    fun exists(replyId: Long): Boolean

    fun insert(reply: SpotAnswerReply, photos: AttachedPhotos): Long

    /** 답글과 그 사진을 물리 삭제한다. */
    fun delete(replyId: Long)

    /** 신고 누적 숨김. 보이는 답글이 이번 호출로 숨겨졌으면 true. */
    fun hideIfVisible(replyId: Long): Boolean
}
