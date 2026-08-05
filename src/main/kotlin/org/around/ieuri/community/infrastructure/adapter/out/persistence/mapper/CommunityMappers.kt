package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
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
fun PostEntity.toDomain(): Post = Post.restore(
    id = requireNotNull(id) { "적재된 글에는 id가 있어야 한다." },
    target = PostTarget(type = targetType, id = targetId),
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
        targetType = target.type,
        targetId = target.id,
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
    return entity
}

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
