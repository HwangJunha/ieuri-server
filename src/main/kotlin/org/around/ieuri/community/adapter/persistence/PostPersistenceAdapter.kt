package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toPost
import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.adapter.persistence.table.PostChipTable
import org.around.ieuri.community.adapter.persistence.table.PostHideTable
import org.around.ieuri.community.adapter.persistence.table.PostLikeTable
import org.around.ieuri.community.adapter.persistence.table.PostPhotoTable
import org.around.ieuri.community.adapter.persistence.table.PostSpotTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.PostTagTable
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.PostTags
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.around.ieuri.community.domain.shared.TargetCategory
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import java.time.Instant

/** 글과 자식 행(카테고리·장소·태그·사진). 읽기는 글 한 행만, 쓰기는 바꾸는 행만 한다. */
@Repository
class PostPersistenceAdapter : PostRepository {

    override fun findById(postId: Long): Post? = PostTable.selectAll()
        .where { (PostTable.id eq postId) and PostTable.deletedAt.isNull() }
        .singleOrNull()?.toPost()

    override fun exists(postId: Long): Boolean = PostTable.select(PostTable.id).where { (PostTable.id eq postId) and PostTable.deletedAt.isNull() }.limit(1).any()

    /** 글을 먼저 넣어 id를 받은 뒤 자식을 붙인다(FK 순서). 장소가 없으면 장소 행을 만들지 않는다. */
    override fun insert(post: Post, category: TargetCategory, spot: PostSpot?, tags: PostTags, photos: AttachedPhotos): Long {
        val postId = PostTable.insertReturning(listOf(PostTable.id)) {
            it[authorId] = post.authorId
            it[title] = post.title
            it[body] = post.body
            it[edited] = post.edited
        }.single()[PostTable.id]

        PostChipTable.insert {
            it[PostChipTable.postId] = postId
            it[PostChipTable.category] = category.name
        }
        spot?.let { writeSpot(postId, it) }
        writeTags(postId, tags)
        writePhotos(postId, photos)
        return postId
    }

    override fun updateContent(post: Post): Boolean {
        val postId = requireNotNull(post.id) { "저장된 글에만 쓴다." }
        return PostTable.update({ (PostTable.id eq postId) and PostTable.deletedAt.isNull() and (PostTable.hidden eq false) }) {
            it[title] = post.title
            it[body] = post.body
            it[edited] = post.edited
            it[updatedAt] = CurrentTimestampWithTimeZone
        } > 0
    }

    override fun changeCategory(postId: Long, category: TargetCategory) {
        PostChipTable.update({ PostChipTable.postId eq postId }) { it[PostChipTable.category] = category.name }
    }

    /** 장소를 떼면 행을 지운다. 위치(geography)는 트리거가 map_x·map_y로 다시 채운다. */
    override fun changeSpot(postId: Long, spot: PostSpot?) {
        if (spot == null) PostSpotTable.deleteWhere { PostSpotTable.postId eq postId } else writeSpot(postId, spot)
    }

    override fun detachSpotsOfCatalogs(catalogIds: List<Long>): Int = PostSpotTable.deleteWhere { PostSpotTable.catalogId inList catalogIds }

    override fun hideIfVisible(postId: Long): Boolean = PostTable.update({
        (PostTable.id eq postId) and PostTable.deletedAt.isNull() and (PostTable.hidden eq false)
    }) { it[hidden] = true } > 0

    override fun replaceTags(postId: Long, tags: PostTags) {
        PostTagTable.deleteWhere { PostTagTable.postId eq postId }
        writeTags(postId, tags)
    }

    override fun replacePhotos(postId: Long, photos: AttachedPhotos) {
        PostPhotoTable.deleteWhere { PostPhotoTable.postId eq postId }
        writePhotos(postId, photos)
    }

    override fun markDeleted(postId: Long, deletedAt: Instant): Boolean = PostTable.update({ (PostTable.id eq postId) and PostTable.deletedAt.isNull() }) {
        it[PostTable.deletedAt] = deletedAt.toTimestamptz()
        it[updatedAt] = CurrentTimestampWithTimeZone
    } > 0

    override fun addHide(memberId: Long, postId: Long): Boolean = PostHideTable.insertIgnore {
        it[PostHideTable.memberId] = memberId
        it[PostHideTable.postId] = postId
    }.insertedCount > 0

    override fun removeHide(memberId: Long, postId: Long): Boolean = PostHideTable.deleteWhere { (PostHideTable.memberId eq memberId) and (PostHideTable.postId eq postId) } > 0

    override fun addLike(postId: Long, memberId: Long): Boolean = PostLikeTable.insertIgnore {
        it[PostLikeTable.postId] = postId
        it[PostLikeTable.memberId] = memberId
    }.insertedCount > 0

    override fun removeLike(postId: Long, memberId: Long): Boolean = PostLikeTable.deleteWhere { (PostLikeTable.postId eq postId) and (PostLikeTable.memberId eq memberId) } > 0

    override fun likeCount(postId: Long): Int = PostTable.select(PostTable.likeCount).where { (PostTable.id eq postId) and PostTable.deletedAt.isNull() }
        .singleOrNull()?.get(PostTable.likeCount) ?: 0

    override fun increaseLikeCount(postId: Long) = increase(postId, PostTable.likeCount)

    override fun decreaseLikeCount(postId: Long) = decrease(postId, PostTable.likeCount)

    override fun increaseCommentCount(postId: Long) = increase(postId, PostTable.commentCount)

    override fun decreaseCommentCount(postId: Long) = decrease(postId, PostTable.commentCount)

    override fun increaseViewCount(postId: Long) = increase(postId, PostTable.viewCount)

    private fun increase(postId: Long, counter: Column<Int>) {
        PostTable.update({ (PostTable.id eq postId) and PostTable.deletedAt.isNull() }) { it[counter] = counter + 1 }
    }

    private fun decrease(postId: Long, counter: Column<Int>) {
        PostTable.update({ (PostTable.id eq postId) and PostTable.deletedAt.isNull() and (counter greater 0) }) { it[counter] = counter - 1 }
    }

    /** map_x는 경도, map_y는 위도다. */
    private fun writeSpot(postId: Long, spot: PostSpot) {
        PostSpotTable.upsert {
            it[PostSpotTable.postId] = postId
            it[catalogId] = spot.catalogId
            it[mapX] = spot.point.longitude
            it[mapY] = spot.point.latitude
        }
    }

    private fun writeTags(postId: Long, tags: PostTags) {
        PostTagTable.batchInsert(tags.values, shouldReturnGeneratedValues = false) { tag ->
            this[PostTagTable.postId] = postId
            this[PostTagTable.name] = tag.name
            this[PostTagTable.normalized] = tag.normalized
        }
    }

    private fun writePhotos(postId: Long, photos: AttachedPhotos) {
        PostPhotoTable.batchInsert(photos.keys.withIndex(), shouldReturnGeneratedValues = false) { (index, key) ->
            this[PostPhotoTable.postId] = postId
            this[PostPhotoTable.objectKey] = key.value
            this[PostPhotoTable.position] = index.toShort()
        }
    }

    override fun markDeletedByAuthor(authorId: Long, deletedAt: Instant): Int = PostTable.update({
        (PostTable.authorId eq authorId) and PostTable.deletedAt.isNull()
    }) { it[PostTable.deletedAt] = deletedAt.toTimestamptz() }

    override fun removeLikesByMember(memberId: Long): List<Long> = PostLikeTable
        .deleteReturning(listOf(PostLikeTable.postId)) { PostLikeTable.memberId eq memberId }
        .map { it[PostLikeTable.postId] }

    override fun removeHidesByMember(memberId: Long): Int = PostHideTable.deleteWhere { PostHideTable.memberId eq memberId }
}
