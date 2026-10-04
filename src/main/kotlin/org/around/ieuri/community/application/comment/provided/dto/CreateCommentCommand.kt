package org.around.ieuri.community.application.comment.provided.dto

/** [parentCommentId]가 있으면 대댓글이다. 대댓글에 답글을 달면 최상위 댓글 아래로 붙는다(깊이 2단계). */
data class CreateCommentCommand(
    val postId: Long,
    val body: String,
    val parentCommentId: Long? = null,
) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
        require(parentCommentId == null || parentCommentId > 0) { "parentCommentId는 양수여야 합니다." }
    }
}
