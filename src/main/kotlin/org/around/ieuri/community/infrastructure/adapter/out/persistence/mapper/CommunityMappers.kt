package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostTagEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ReportEntity

/**
 * 엔티티↔도메인 변환. 형태만 옮기며 업무 규칙도 I/O도 없다.
 */

/**
 * 글 엔티티를 도메인으로.
 *
 * 파싱되지 않는 사진 키는 **조용히 버리지 않고 실패시킨다**. 적재 시점에 형식을 통과한
 * 값이므로 파싱 실패는 데이터 손상이며, 그냥 넘기면 글에서 사진 한 장이 이유 없이 사라진다.
 */
/**
 * 카테고리·장소는 별도 테이블이라 **호출자가 읽어 넘긴다** — 엔티티에 연관으로 두면
 * nullable @OneToOne이 lazy가 되지 않아 조회마다 N+1이 붙는다(PostEntity 주석 참고).
 */
fun PostEntity.toDomain(category: TargetCategory, spotId: Long?): Post = Post.restore(
    id = requireNotNull(id) { "적재된 글에는 id가 있어야 한다." },
    category = category,
    spotId = spotId,
    authorId = authorId,
    tags = tags.map { PostTag.restore(it.name, it.normalized) },
    title = title,
    body = body,
    photoKeys = photos.sortedBy { it.position }.map {
        requireNotNull(PhotoObjectKey.parse(it.objectKey)) {
            "적재된 사진 키(${it.objectKey})의 형식이 올바르지 않다."
        }
    },
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
 * 사진은 부모를 역참조하므로 엔티티를 만든 **뒤에** 붙인다 — 순서를 뒤집으면 아직 없는
 * 부모를 가리키게 된다.
 */
fun Post.toNewEntity(): PostEntity {
    val entity = PostEntity(
        authorId = authorId,
        title = title,
        body = body,
        likeCount = likeCount,
        commentCount = commentCount,
        hidden = hidden,
        edited = edited,
        deletedAt = deletedAt,
    )
    entity.photos = photoKeys.mapIndexed { index, key ->
        PostPhotoEntity(post = entity, objectKey = key.value, position = index.toShort())
    }.toMutableList()
    entity.tags = tags.map {
        PostTagEntity(post = entity, name = it.name, normalized = it.normalized)
    }.toMutableList()
    // 카테고리·장소 연결은 여기서 만들지 않는다 — 글의 id가 있어야 만들 수 있어
    // 어댑터가 글을 저장한 뒤 붙인다.
    return entity
}

/** 글의 id가 정해진 **뒤에** 만든다 — post_id가 PK다. */
fun Post.toChipEntity(postId: Long): PostChipEntity =
    PostChipEntity(postId = postId, category = category)

/** 장소는 선택이라 없으면 null이며, 그때는 연결 행 자체를 만들지 않는다. */
fun Post.toSpotEntity(postId: Long): PostSpotEntity? =
    spotId?.let { PostSpotEntity(postId = postId, spotId = it) }

fun CommentEntity.toDomain(): Comment = Comment.restore(
    id = requireNotNull(id) { "적재된 댓글에는 id가 있어야 한다." },
    postId = postId,
    authorId = authorId,
    body = body,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 댓글에는 createdAt이 있어야 한다." },
)

fun Comment.toNewEntity(): CommentEntity = CommentEntity(
    postId = postId,
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
