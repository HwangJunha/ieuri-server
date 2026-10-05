package org.around.ieuri.community.application.post.provided

import org.around.ieuri.community.application.post.provided.dto.CreatePostCommand
import org.around.ieuri.community.application.post.provided.dto.EditPostCommand
import org.around.ieuri.community.application.post.provided.dto.PostTargetCommand
import org.around.ieuri.community.domain.shared.ContentLanguage

/**
 * 글 쓰기·좋아요·개인 숨김. memberId는 인증 컨텍스트에서 온다.
 * - `language`는 장소 존재 판정의 기준이다: 이 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE.
 */
interface PostCommandUseCase {
    /** 생성된 글의 id만 돌려준다. */
    fun createPost(command: CreatePostCommand, memberId: Long, language: ContentLanguage): Long

    /** 본인 글만, 숨겨지지 않은 글만 고칠 수 있다. */
    fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage)

    fun deletePost(command: PostTargetCommand, memberId: Long)

    /** 멱등. 연산 후의 좋아요 수를 돌려준다. */
    fun like(command: PostTargetCommand, memberId: Long): Int

    /** 멱등. 연산 후의 좋아요 수를 돌려준다. */
    fun unlike(command: PostTargetCommand, memberId: Long): Int

    /** 내 화면에서만 감춘다(신고 누적 숨김과 다르다). 멱등이며 작성자 여부를 보지 않는다. */
    fun hide(command: PostTargetCommand, memberId: Long)

    /** 멱등. 글의 존재를 보지 않는다 — 삭제된 글의 숨김 기록도 지울 수 있다. */
    fun unhide(command: PostTargetCommand, memberId: Long)

    /** 조회수 +1. 삭제된 글이면 아무것도 하지 않는다. */
    fun recordView(postId: Long)
}
