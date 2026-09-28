package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.BlockUserCommand
import org.around.ieuri.community.application.command.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.command.CreateCommentCommand
import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeleteCommentCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.DeletePostImageCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.command.HidePostCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.command.ReportContentCommand
import org.around.ieuri.community.application.query.PostImageConfirmResult
import org.around.ieuri.community.application.query.PostImageUploadUrlView
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 글 쓰기 유스케이스. memberId는 인증 컨텍스트에서 온다.
 * - `language`는 장소 존재 검사의 기준이다(SPEC-0029): 카탈로그가 없으면 SPOT_NOT_FOUND,
 *   이 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE.
 */
interface PostCommandUseCase {
    /** 생성된 글의 id만 돌려준다. */
    fun createPost(command: CreatePostCommand, memberId: Long, language: ContentLanguage): Long

    /** 제목·본문 수정. 본인 글만, 숨겨지지 않은 글만 고칠 수 있다. */
    fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage)

    fun deletePost(command: DeletePostCommand, memberId: Long)
}

interface CommentCommandUseCase {
    /** 생성된 댓글의 id만 돌려준다. */
    fun createComment(command: CreateCommentCommand, memberId: Long): Long

    fun deleteComment(command: DeleteCommentCommand, memberId: Long)
}

/** 좋아요. 양방향 모두 멱등이며 연산 후의 좋아요 수를 돌려준다. */
interface PostLikeCommandUseCase {
    fun like(command: LikePostCommand, memberId: Long): Int

    fun unlike(command: LikePostCommand, memberId: Long): Int
}

/** 글 하나 단위의 개인 숨김. 중복 숨김·미숨김 해제 모두 오류가 아니라 멱등 no-op이다. */
interface PostHideCommandUseCase {
    fun hide(command: HidePostCommand, memberId: Long)

    fun unhide(command: HidePostCommand, memberId: Long)
}

/** 차단. 중복 차단·미차단 해제 모두 오류가 아니라 멱등 no-op이다. */
interface BlockCommandUseCase {
    fun block(command: BlockUserCommand, memberId: Long)

    fun unblock(command: BlockUserCommand, memberId: Long)
}

interface ReportCommandUseCase {
    fun report(command: ReportContentCommand, memberId: Long)
}

/** 사진 업로드. 서버는 바이트를 중계하지 않고 URL 발급과 업로드 확인만 한다. */
interface PostImageUploadUseCase {
    /** 여러 장을 한 번에 발급한다. 하나라도 어긋나면 전부 거부한다. */
    fun issueUploadUrls(
        command: IssuePostImageUploadUrlsCommand,
        memberId: Long,
    ): List<PostImageUploadUrlView>

    /** 여러 장을 한 번에 확인한다. 장별로 성패가 갈린다. */
    fun confirmUploads(
        command: ConfirmPostImageUploadsCommand,
        memberId: Long,
    ): PostImageConfirmResult

    /** 업로드한 사진을 지운다. 글에 붙이기 전에도, 붙인 뒤에도 쓴다. */
    fun deleteImage(command: DeletePostImageCommand, memberId: Long)
}
