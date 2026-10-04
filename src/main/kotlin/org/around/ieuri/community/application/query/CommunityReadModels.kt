package org.around.ieuri.community.application.query

import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Instant

// 커뮤니티 읽기(CQRS) read model. 리졸버가 GraphQL 응답 타입으로 매핑한다.

/** 피드 카드의 장소 칩. [id]는 `spot.id`가 아니라 카탈로그 id다(SPEC-0019). */
data class PostSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /** spot이 속한 카테고리. 글 작성 시 기본값 제안에 쓴다. */
    val category: TargetCategory?,
    /** 경도·위도. 글을 만들 때 글에 복사해 둔다. 조회 응답으로는 내려주지 않는다. */
    val longitude: Double,
    val latitude: Double,
)

/** 글·댓글에 실리는 최소 작성자 정보. 커뮤니티 표면에는 이메일·권한·상태가 필요 없다. */
data class PostAuthorView(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

/**
 * 피드·단건 조회의 글 한 건. community_post 한 행이 가진 것만 담는다.
 * - 태그·사진·카테고리·장소·좋아요 여부는 필드를 고를 때 DataLoader가 따로 읽는다.
 */
data class PostView(
    val id: Long,
    val title: String,
    val body: String,
    val authorId: Long,
    val likeCount: Int,
    val commentCount: Int,
    /** 조회수. 상세 조회에서만 오르며 중복을 방지하지 않는다. */
    val viewCount: Int,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    /** 수정된 적이 있는지. 클라이언트가 "수정됨"을 표시한다. */
    val edited: Boolean,
    val createdAt: Instant,
)

data class CommentView(
    val id: Long,
    /** 최상위 댓글이면 null. 있으면 그 댓글에 달린 대댓글이다. */
    val parentCommentId: Long?,
    val body: String,
    val author: PostAuthorView,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)

/** 차단 목록 항목. [blockedAt]은 노드가 아니라 엣지의 값이며 리졸버가 옮긴다. */
data class BlockedUserView(
    val member: PostAuthorView,
    val blockedAt: Instant,
)

/** 커뮤니티 상단 칩 하나. 표시 문구는 담지 않는다 — 앱이 [category]를 언어별로 매핑한다. */
data class CommunityChipView(
    val category: TargetCategory,
)

/** 발급된 presigned URL 한 건. */
data class PostImageUploadUrlView(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)

/** 확인 결과. 장별로 성패가 갈린다 — 한 장이 실패해도 나머지는 확정된다. */
data class PostImageConfirmResult(
    val confirmed: List<String>,
    val failed: List<PostImageConfirmFailure>,
)

data class PostImageConfirmFailure(
    val objectKey: String,
    /** 실패 사유. 미업로드·타인 키·형식 위반을 구분하지 않는다(키 존재를 캐낼 수 있어서다). */
    val code: String,
)

/**
 * 글 피드 조회 입력. 인자 조합으로 범위가 정해진다 — 대상 하나, 좌표 반경, 또는 전체다.
 * - [radiusMeters]는 좌표를 줄 때만 의미가 있다.
 */
data class PostsQuery(
    /** 이 장소(카탈로그)에 붙은 글만. null이면 장소로 좁히지 않는다. */
    val catalogId: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020). */
    val categories: Set<TargetCategory>,
    /** 태그 필터. 정규화해 매칭하므로 대소문자를 가리지 않는다. */
    val tag: String?,
    /** 정렬 방식. 좌표가 없으면 DISTANCE도 LATEST로 떨어진다. */
    val sort: PostSortOrder,
    /** true면 숨긴 글만 본다(해제 화면). 기본은 false로 숨긴 글을 제외한다. */
    val hiddenByViewer: Boolean = false,
    val first: Int,
    val after: String?,
) {
    /** 좌표가 둘 다 있어야 반경 조회다. 하나만 주면 좌표를 안 준 것으로 본다. */
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

data class PostCommentsQuery(
    val postId: Long,
    val first: Int,
    val after: String?,
)

data class BlockedUsersQuery(
    val first: Int,
    val after: String?,
)

/** 내가 쓴 글 조회 조건(SPEC-0011 R3). 위치·카테고리로 좁히지 않는다. */
data class MyPostsQuery(
    val first: Int,
    val after: String?,
)
