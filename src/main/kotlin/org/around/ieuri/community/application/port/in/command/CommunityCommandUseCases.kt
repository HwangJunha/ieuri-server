package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.BlockUserCommand
import org.around.ieuri.community.application.command.ConfirmPostImageUploadCommand
import org.around.ieuri.community.application.command.CreateCommentCommand
import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeleteCommentCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlCommand
import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.command.ReportContentCommand
import org.around.ieuri.community.application.query.PostImageUploadUrlView

/**
 * 글 쓰기 유스케이스. [memberId]는 인증된 회원이며 리졸버가 SecurityContext에서 확립한다.
 */
interface PostCommandUseCase {
    /**
     * 생성된 글의 id. 글 전체를 돌려주지 않는다 — 응답에 spot·작성자를 채우려면 다른 도메인을
     * 왕복해야 하는데, 클라이언트는 어차피 `post(id:)`로 다시 조회한다.
     */
    fun createPost(command: CreatePostCommand, memberId: Long): Long

    /**
     * 제목·본문 수정. 본인 글만, 숨겨지지 않은 글만 고칠 수 있다.
     */
    fun editPost(command: EditPostCommand, memberId: Long)

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
    fun issueUploadUrl(command: IssuePostImageUploadUrlCommand, memberId: Long): PostImageUploadUrlView

    /** 검증을 통과한 최종 오브젝트 키. 글 생성에는 이 키를 넘긴다. */
    fun confirmUpload(command: ConfirmPostImageUploadCommand, memberId: Long): String
}
