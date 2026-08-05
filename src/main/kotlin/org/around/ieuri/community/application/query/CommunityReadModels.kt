package org.around.ieuri.community.application.query

import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostTopic
import java.time.Instant

/**
 * 읽기(CQRS) 전용 read model. 애그리게이트를 우회해 QueryDSL이 직접 프로젝션한 결과이며
 * 도메인/JPA 어느 쪽도 아니다. 리졸버가 이 DTO를 GraphQL 응답 타입으로 매핑한다.
 */

/**
 * 피드 카드의 대상 칩. 대상 도메인의 전체 모델이 아니라 칩에 필요한 최소 정보만 담는다.
 *
 * 종류가 무엇이든 같은 모양이다 — 관광지든 상품이든 카드에 필요한 것은 제목과 썸네일뿐이며,
 * 그래서 종류가 늘어도 이 타입과 피드 렌더링은 그대로다.
 */
data class PostTargetView(
    val type: PostTargetType,
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /** 상단 추천 칩이 거르는 축. 카테고리 개념이 없는 종류는 null이다. */
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
    val topic: PostTopic,
    val target: PostTargetView,
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

/**
 * 글 피드 조회 입력. 인자 조합으로 범위가 정해진다 — 대상 하나, 좌표 반경, 또는 전체다.
 *
 * [radiusMeters]·[category]는 좌표를 줄 때만 의미가 있다.
 */
data class PostsQuery(
    val target: PostTarget?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val category: TargetCategory?,
    val topic: PostTopic?,
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
