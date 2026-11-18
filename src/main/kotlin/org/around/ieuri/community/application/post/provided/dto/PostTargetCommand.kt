package org.around.ieuri.community.application.post.provided.dto

/** 삭제·좋아요·숨김처럼 글 하나를 가리키는 입력. */
data class PostTargetCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}
