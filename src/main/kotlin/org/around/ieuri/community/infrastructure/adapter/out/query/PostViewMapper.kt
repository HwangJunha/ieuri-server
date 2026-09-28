package org.around.ieuri.community.infrastructure.adapter.out.query

import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity

/** 글 엔티티 → 읽기 DTO. community_post 한 행이 가진 것만 옮긴다(나머지는 DataLoader). */
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
