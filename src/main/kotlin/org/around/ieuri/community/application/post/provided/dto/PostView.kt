package org.around.ieuri.community.application.post.provided.dto

import java.time.Instant

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
