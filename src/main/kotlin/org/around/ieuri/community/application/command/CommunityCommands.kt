package org.around.ieuri.community.application.command

import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType

/**
 * 커뮤니티 쓰기 입력. 형식 검증만 여기서 하고, 업무 불변식은 도메인이 판정한다.
 * 호출자(memberId)는 인자가 아니라 인증 컨텍스트에서 오므로 커맨드에 담지 않는다.
 */

data class CreatePostCommand(
    /** 글의 카테고리. 필수 — 상단 칩이 이 값으로 거른다. */
    val category: TargetCategory,
    /** 연결할 장소. **선택이다** — null이면 카테고리에만 속하는 글이다. */
    val spotId: Long?,
    /** 사용자가 입력한 태그. 최대 5개이며 중복은 하나로 합쳐진다. */
    val tags: List<String>,
    val title: String,
    val body: String,
    /** 확인 단계를 통과한 오브젝트 키. 배열 순서가 표시 순서다. */
    val photoKeys: List<String>,
)

/**
 * 글 수정 입력. 제목·본문만 바꾼다 — 사진·주제·대상은 불변이다.
 */
data class EditPostCommand(
    val postId: Long,
    val title: String,
    val body: String,
    /** 사진 키 전체 목록으로 교체. null이면 사진을 건드리지 않는다. */
    val photoKeys: List<String>?,
) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}

data class DeletePostCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}

data class CreateCommentCommand(val postId: Long, val body: String) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}

data class DeleteCommentCommand(val commentId: Long) {
    init {
        require(commentId > 0) { "commentId는 양수여야 합니다." }
    }
}

data class LikePostCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}

data class BlockUserCommand(val targetMemberId: Long) {
    init {
        require(targetMemberId > 0) { "targetMemberId는 양수여야 합니다." }
    }
}

data class ReportContentCommand(
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val detail: String?,
) {
    init {
        require(targetId > 0) { "targetId는 양수여야 합니다." }
    }
}

/**
 * presigned URL 발급 입력. contentType·contentLength는 **서명에 포함되므로** 클라이언트가
 * 업로드 시 선언한 값과 정확히 일치해야 한다 — 선언과 다른 파일을 올리면 R2가 거부한다.
 */
data class PostImageUploadRequest(
    val contentType: String,
    val contentLength: Long,
)

/** 여러 장을 한 번에 발급한다. 하나라도 어긋나면 전부 발급하지 않는다. */
data class IssuePostImageUploadUrlsCommand(val images: List<PostImageUploadRequest>)

/** 여러 장을 한 번에 확인한다. 장별로 성패가 갈린다. */
data class ConfirmPostImageUploadsCommand(val objectKeys: List<String>)

data class DeletePostImageCommand(val objectKey: String)
