package org.around.ieuri.community.application.query

import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostTag
import java.time.Instant

/**
 * 읽기(CQRS) 전용 read model. 애그리게이트를 우회해 QueryDSL이 직접 프로젝션한 결과이며
 * 도메인/JPA 어느 쪽도 아니다. 리졸버가 이 DTO를 GraphQL 응답 타입으로 매핑한다.
 */

/** 피드 카드의 장소 칩. spot 전체가 아니라 칩에 필요한 최소 정보만 담는다. */
data class PostSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /** spot이 속한 카테고리. 글 작성 시 기본값 제안에 쓴다. */
    val category: TargetCategory?,
)

/** 글·댓글에 실리는 최소 작성자 정보. 커뮤니티 표면에는 이메일·권한·상태가 필요 없다. */
data class PostAuthorView(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

/**
 * 피드·단건 조회의 글 한 건.
 *
 * [likedByViewer]·[ownedByViewer]는 호출자에 따라 달라지는 값이라 프로젝션 단계에서 채운다.
 * 미인증 호출자에게 [likedByViewer]는 null이 아니라 false다.
 */
data class PostView(
    val id: Long,
    val title: String,
    val body: String,
    /** 사용자가 입력한 태그. 표시는 원문 그대로다. */
    val tags: List<String>,
    val category: TargetCategory,
    /** 연결된 장소. 없으면 null이며, 카드는 장소 칩 없이 그려진다. */
    val spot: PostSpotView?,
    val author: PostAuthorView,
    val photoKeys: List<String>,
    val likeCount: Int,
    val commentCount: Int,
    val likedByViewer: Boolean,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    /** 수정된 적이 있는지. 클라이언트가 "수정됨"을 표시한다. */
    val edited: Boolean,
    val createdAt: Instant,
)

data class CommentView(
    val id: Long,
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

/**
 * 커뮤니티 상단 칩 하나. **표시 문구는 담지 않는다** — 앱이 [category]를 언어별로 매핑한다.
 */
data class CommunityChipView(
    val category: TargetCategory,
    val position: Int,
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
 *
 * [radiusMeters]·[category]는 좌표를 줄 때만 의미가 있다.
 */
data class PostsQuery(
    /** 이 장소에 붙은 글만. null이면 장소로 좁히지 않는다. */
    val spotId: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val category: TargetCategory?,
    /** 태그 필터. 정규화해 매칭하므로 대소문자를 가리지 않는다. */
    val tag: String?,
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
