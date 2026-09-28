package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.SpotPoint
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostTagEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ReportEntity

/*
 * 엔티티↔도메인 변환. 형태만 옮기며 업무 규칙도 I/O도 없다.
 */

/*
 * 글 엔티티를 도메인으로.
 *
 * 파싱되지 않는 사진 키는 **조용히 버리지 않고 실패시킨다**. 적재 시점에 형식을 통과한
 * 값이므로 파싱 실패는 데이터 손상이며, 그냥 넘기면 글에서 사진 한 장이 이유 없이 사라진다.
 */
/**
 * 카테고리·장소는 별도 테이블이라 **호출자가 읽어 넘긴다** — 엔티티에 연관으로 두면
 * nullable @OneToOne이 lazy가 되지 않아 조회마다 N+1이 붙는다(PostEntity 주석 참고).
 */
fun PostEntity.toDomain(
    category: TargetCategory,
    catalogId: Long?,
    spotPoint: SpotPoint?,
    tags: List<PostTag>,
    photoKeys: List<PhotoObjectKey>,
): Post = Post.restore(
    id = requireNotNull(id) { "적재된 글에는 id가 있어야 한다." },
    category = category,
    catalogId = catalogId,
    spotPoint = spotPoint,
    authorId = authorId,
    tags = tags,
    title = title,
    body = body,
    photoKeys = photoKeys,
    likeCount = likeCount,
    commentCount = commentCount,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 글에는 createdAt이 있어야 한다." },
    edited = edited,
)

/**
 * 도메인 글을 새 엔티티로. id·시각은 DB가 채운다.
 *
 * **자식(사진·태그·연결)은 붙이지 않는다.** 전부 글의 id가 정해진 뒤에야 만들 수 있어
 * 어댑터가 저장 후 따로 적재한다.
 */
fun Post.toNewEntity(): PostEntity = PostEntity(
    authorId = authorId,
    title = title,
    body = body,
    likeCount = likeCount,
    commentCount = commentCount,
    hidden = hidden,
    edited = edited,
    deletedAt = deletedAt,
)

/** 배열 순서가 표시 순서다 — position에 그대로 싣는다. */
fun Post.toPhotoEntities(postId: Long): List<PostPhotoEntity> = photoKeys.mapIndexed { index, key ->
    PostPhotoEntity(postId = postId, objectKey = key.value, position = index.toShort())
}

fun Post.toTagEntities(postId: Long): List<PostTagEntity> = tags.map { PostTagEntity(postId = postId, name = it.name, normalized = it.normalized) }

fun PostPhotoEntity.toPhotoKey(): PhotoObjectKey = requireNotNull(PhotoObjectKey.parse(objectKey)) {
    "적재된 사진 키($objectKey)의 형식이 올바르지 않다."
}

fun PostTagEntity.toPostTag(): PostTag = PostTag.restore(name, normalized)

/** 글의 id가 정해진 **뒤에** 만든다 — post_id가 PK다. */
fun Post.toChipEntity(postId: Long): PostChipEntity = PostChipEntity(postId = postId, category = category)

/**
 * 장소는 선택이라 없으면 null이며, 그때는 연결 행 자체를 만들지 않는다.
 *
 * 좌표를 함께 적재한다 — 지역 피드가 tourism에 spot 목록을 되묻지 않게 하는 값이다.
 * geography 컬럼은 트리거가 map_x/map_y에서 파생시키므로 여기서 다루지 않는다.
 */
fun Post.toSpotEntity(postId: Long): PostSpotEntity? {
    val point = spotPoint ?: return null
    return PostSpotEntity(
        postId = postId,
        catalogId = requireNotNull(catalogId) { "좌표가 있으면 장소도 있어야 한다." },
        mapX = point.longitude,
        mapY = point.latitude,
    )
}

fun CommentEntity.toDomain(): Comment = Comment.restore(
    id = requireNotNull(id) { "적재된 댓글에는 id가 있어야 한다." },
    postId = postId,
    parentCommentId = parentCommentId,
    authorId = authorId,
    body = body,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 댓글에는 createdAt이 있어야 한다." },
)

fun Comment.toNewEntity(): CommentEntity = CommentEntity(
    postId = postId,
    parentCommentId = parentCommentId,
    authorId = authorId,
    body = body,
    hidden = hidden,
    deletedAt = deletedAt,
)

fun PostLikeEntity.toDomain(): PostLike = PostLike.restore(
    id = requireNotNull(id) { "적재된 좋아요에는 id가 있어야 한다." },
    postId = postId,
    memberId = memberId,
    createdAt = requireNotNull(createdAt) { "적재된 좋아요에는 createdAt이 있어야 한다." },
)

fun PostLike.toNewEntity(): PostLikeEntity = PostLikeEntity(postId = postId, memberId = memberId)

fun BlockEntity.toDomain(): Block = Block.restore(
    id = requireNotNull(id) { "적재된 차단에는 id가 있어야 한다." },
    blockerId = blockerId,
    blockedId = blockedId,
    createdAt = requireNotNull(createdAt) { "적재된 차단에는 createdAt이 있어야 한다." },
)

fun Block.toNewEntity(): BlockEntity = BlockEntity(blockerId = blockerId, blockedId = blockedId)

fun Report.toNewEntity(): ReportEntity = ReportEntity(
    reporterId = reporterId,
    targetType = targetType,
    targetId = targetId,
    reason = reason,
    detail = detail,
)
