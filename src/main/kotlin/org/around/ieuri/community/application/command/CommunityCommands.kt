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
    /** 연결할 장소(카탈로그 id). **선택이다** — null이면 카테고리에만 속하는 글이다. */
    val catalogId: Long?,
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
/**
 * 글 수정. **null은 "건드리지 않는다"는 뜻이고 빈 값과 다르다** — 예컨대 photoKeys가 null이면
 * 사진을 그대로 두고, 빈 배열이면 전부 뗀다.
 *
 * [catalogId]만 예외다. 장소를 떼는 것과 그대로 두는 것을 null 하나로 구분할 수 없어
 * [changeSpot] 플래그로 의도를 받는다.
 */
data class EditPostCommand(
    val postId: Long,
    val title: String,
    val body: String,
    /** 사진 키 전체 목록으로 교체. null이면 사진을 건드리지 않는다. */
    val photoKeys: List<String>?,
    /** 바꿀 카테고리. null이면 그대로 둔다. */
    val category: TargetCategory? = null,
    /**
     * true면 [catalogId]로 장소를 바꾼다 — **catalogId가 null이면 장소를 뗀다.**
     * false면 catalogId 값과 무관하게 장소를 건드리지 않는다.
     */
    val changeSpot: Boolean = false,
    val catalogId: Long? = null,
    /** 태그 전체 목록으로 교체. null이면 태그를 건드리지 않는다. */
    val tags: List<String>? = null,
) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
        require(catalogId == null || catalogId > 0) { "catalogId는 양수여야 합니다." }
        require(changeSpot || catalogId == null) {
            "장소를 바꾸지 않으면서 catalogId를 줄 수 없습니다."
        }
    }
}

data class DeletePostCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}

/** [parentCommentId]가 있으면 대댓글이다. 대댓글에 답글을 달면 최상위에 눕는다(깊이 2단계). */
data class CreateCommentCommand(
    val postId: Long,
    val body: String,
    val parentCommentId: Long? = null,
) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
        require(parentCommentId == null || parentCommentId > 0) {
            "parentCommentId는 양수여야 합니다."
        }
    }
}

data class DeleteCommentCommand(val commentId: Long) {
    init {
        require(commentId > 0) { "commentId는 양수여야 합니다." }
    }
}

/** 글 숨김·해제. 내 화면에서만 감추는 것이라 작성자 여부를 보지 않는다. */
data class HidePostCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
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
