package org.around.ieuri.community.infrastructure.adapter.out.query

import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity

/**
 * 글 엔티티 → 읽기 DTO. **최신순·거리순 두 어댑터가 공유한다** — 각자 두면 한쪽만 고쳐진다.
 *
 * community_post 한 행이 가진 것만 옮긴다. 태그·사진·카테고리·장소·좋아요는 각각 다른
 * 테이블에 있어 클라이언트가 그 필드를 고른 경우에만 DataLoader가 읽는다.
 */
fun PostEntity.toPostView(viewer: ViewerContext): PostView = PostView(
    id = requireNotNull(id),
    title = title,
    body = body,
    authorId = authorId,
    likeCount = likeCount,
    commentCount = commentCount,
    viewCount = viewCount,
    ownedByViewer = authorId == viewer.memberId,
    hidden = hidden,
    edited = edited,
    createdAt = requireNotNull(createdAt),
)
