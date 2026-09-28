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
 * 글 쓰기 유스케이스. [memberId]는 인증된 회원이며 리졸버가 SecurityContext에서 확립한다.
 *
 * `language`는 요청의 활성 언어이며 **장소 존재 검사의 기준이다**(SPEC-0029 R-16) —
 * 카탈로그가 아예 없으면 SPOT_NOT_FOUND, 있으나 이 언어에 원본이 없으면
 * SPOT_NOT_FOUND_IN_LOCALE이다. 커맨드가 아니라 인자로 받는 이유는 커맨드가 클라이언트
 * 입력만 들고 있기 때문이다(memberId와 같은 이유).
 */
interface PostCommandUseCase {
    /**
     * 생성된 글의 id. 글 전체를 돌려주지 않는다 — 응답에 spot·작성자를 채우려면 다른 도메인을
     * 왕복해야 하는데, 클라이언트는 어차피 `post(id:)`로 다시 조회한다.
     */
    fun createPost(command: CreatePostCommand, memberId: Long, language: ContentLanguage): Long

    /**
     * 제목·본문 수정. 본인 글만, 숨겨지지 않은 글만 고칠 수 있다.
     */
    fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage)

    fun deletePost(command: DeletePostCommand, memberId: Long)
}

interface CommentCommandUseCase {
    /** 생성된 댓글의 id. 이유는 [PostCommandUseCase.createPost]와 같다. */
    fun createComment(command: CreateCommentCommand, memberId: Long): Long

    fun deleteComment(command: DeleteCommentCommand, memberId: Long)
}

/** 좋아요. 양방향 모두 멱등이며 연산 후의 좋아요 수를 돌려준다. */
interface PostLikeCommandUseCase {
    fun like(command: LikePostCommand, memberId: Long): Int

    fun unlike(command: LikePostCommand, memberId: Long): Int
}

/**
 * 글 숨김. **차단과 다른 축이다** — 차단은 사람 단위, 이쪽은 글 하나다.
 * 개별 콘텐츠를 감출 수단이 없으면 글 하나 때문에 작성자를 통째로 차단해야 한다.
 *
 * 중복 숨김·미숨김 해제 모두 오류가 아니라 멱등 no-op이다.
 */
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

/**
 * 사진 업로드. 서버는 바이트를 중계하지 않고 발급과 확인만 한다.
 * 두 단계가 나뉜 이유는 확인 시점에야 실제 크기·매직바이트를 볼 수 있기 때문이다.
 */
interface PostImageUploadUseCase {
    /** 여러 장을 한 번에 발급한다 — 5장이면 왕복 1회다. 하나라도 어긋나면 전부 거부한다. */
    fun issueUploadUrls(
        command: IssuePostImageUploadUrlsCommand,
        memberId: Long,
    ): List<PostImageUploadUrlView>

    /** 여러 장을 한 번에 확인한다. **장별로 성패가 갈린다.** */
    fun confirmUploads(
        command: ConfirmPostImageUploadsCommand,
        memberId: Long,
    ): PostImageConfirmResult

    /**
     * 업로드한 사진을 지운다. 글에 붙이기 전에도, 붙인 뒤에도 쓴다 —
     * 클라이언트가 이미지를 치울 통로가 있어야 한다.
     */
    fun deleteImage(command: DeletePostImageCommand, memberId: Long)
}
