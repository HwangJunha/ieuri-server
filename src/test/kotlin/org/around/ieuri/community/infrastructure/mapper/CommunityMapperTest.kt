package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 엔티티↔도메인 변환. **필드마다 서로 다른 값**을 넣어 매핑이 뒤바뀌면 실패하게 한다 —
 * 통째로 비교하면 어느 필드가 틀렸는지 알 수 없다.
 */
class CommunityMapperTest {

    private val createdAt = Instant.parse("2026-08-05T01:02:03Z")
    private val deletedAt = Instant.parse("2026-08-06T04:05:06Z")

    @Test
    fun `글 엔티티가 도메인으로 필드마다 옮겨진다`() {
        val entity = PostEntity(
            id = 1L,
            authorId = 3L,
            title = "제목",
            body = "본문",
            likeCount = 4,
            commentCount = 5,
            hidden = true,
            deletedAt = deletedAt,
            createdAt = createdAt,
            updatedAt = Instant.parse("2026-08-07T00:00:00Z"),
        )
        val uuid = UUID.randomUUID()
        entity.photos = mutableListOf(
            PostPhotoEntity(post = entity, objectKey = "images/3/$uuid", position = 0),
        )
        entity.chip = PostChipEntity(post = entity, category = TargetCategory.TOURIST_SPOT)

        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        
        assertEquals(3L, domain.authorId)
        assertEquals("본문", domain.body)
        assertEquals(4, domain.likeCount)
        assertEquals(5, domain.commentCount)
        assertEquals(true, domain.hidden)
        assertEquals(deletedAt, domain.deletedAt)
        assertEquals(createdAt, domain.createdAt)
        assertEquals(listOf("images/3/$uuid"), domain.photoKeys.map { it.value })
    }

    @Test
    fun `글 사진은 position 순서대로 옮겨진다`() {
        val entity = PostEntity(
            id = 1L, authorId = 3L,
            title = "제목", body = "본문",
            createdAt = createdAt, updatedAt = createdAt,
        )
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        // 일부러 뒤섞어 담는다 — 정렬이 빠지면 실패한다.
        entity.photos = mutableListOf(
            PostPhotoEntity(post = entity, objectKey = "images/3/$second", position = 1),
            PostPhotoEntity(post = entity, objectKey = "images/3/$first", position = 0),
        )

        assertEquals(
            listOf("images/3/$first", "images/3/$second"),
            entity.also { it.chip = PostChipEntity(post = it, category = TargetCategory.TOURIST_SPOT) }
                .toDomain().photoKeys.map { it.value },
        )
    }

    @Test
    fun `nullable 필드가 모두 null인 글도 옮겨진다`() {
        val entity = PostEntity(
            id = 1L, authorId = 3L,
            title = "제목", body = "본문",
            deletedAt = null, createdAt = createdAt, updatedAt = createdAt,
        )

        entity.chip = PostChipEntity(post = entity, category = TargetCategory.TOURIST_SPOT)

        val domain = entity.toDomain()

        assertNull(domain.deletedAt)
        assertTrue(domain.photoKeys.isEmpty())
    }

    @Test
    fun `도메인 글이 새 엔티티로 필드마다 옮겨지고 사진에 순서가 부여된다`() {
        val keyA = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val keyB = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val domain = Post.create(
            category = TargetCategory.TOURIST_SPOT,
        spotId = 2L, authorId = 3L, tags = emptyList(),
            title = "제목", body = "본문", photoKeys = listOf(keyA, keyB),
        )

        val entity = domain.toNewEntity()

        assertNull(entity.id)
        assertEquals(3L, entity.authorId)
        assertEquals("본문", entity.body)
        assertEquals(0, entity.likeCount)
        assertEquals(0, entity.commentCount)
        assertEquals(false, entity.hidden)
        assertNull(entity.deletedAt)
        assertEquals(listOf(keyA.value, keyB.value), entity.photos.map { it.objectKey })
        assertEquals(listOf<Short>(0, 1), entity.photos.map { it.position })
        // 사진은 부모를 역참조해야 FK가 채워진다.
        assertTrue(entity.photos.all { it.post === entity })
    }

    @Test
    fun `댓글 엔티티가 도메인으로 필드마다 옮겨진다`() {
        val entity = CommentEntity(
            id = 1L, postId = 2L, authorId = 3L, body = "댓글",
            hidden = true, deletedAt = deletedAt, createdAt = createdAt, updatedAt = createdAt,
        )

        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals(2L, domain.postId)
        assertEquals(3L, domain.authorId)
        assertEquals("댓글", domain.body)
        assertEquals(true, domain.hidden)
        assertEquals(deletedAt, domain.deletedAt)
        assertEquals(createdAt, domain.createdAt)
    }

    @Test
    fun `nullable 필드가 null인 댓글도 옮겨진다`() {
        val entity = CommentEntity(
            id = 1L, postId = 2L, authorId = 3L, body = "댓글",
            hidden = false, deletedAt = null, createdAt = createdAt, updatedAt = createdAt,
        )
        assertNull(entity.toDomain().deletedAt)
    }

    @Test
    fun `도메인 댓글이 새 엔티티로 옮겨진다`() {
        val entity = Comment.create(postId = 2L, authorId = 3L, body = "댓글").toNewEntity()

        assertNull(entity.id)
        assertEquals(2L, entity.postId)
        assertEquals(3L, entity.authorId)
        assertEquals("댓글", entity.body)
        assertEquals(false, entity.hidden)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `좋아요가 양방향으로 옮겨진다`() {
        val entity = PostLikeEntity(id = 1L, postId = 2L, memberId = 3L, createdAt = createdAt)
        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals(2L, domain.postId)
        assertEquals(3L, domain.memberId)
        assertEquals(createdAt, domain.createdAt)

        val newEntity = PostLike.create(postId = 4L, memberId = 5L).toNewEntity()
        assertNull(newEntity.id)
        assertEquals(4L, newEntity.postId)
        assertEquals(5L, newEntity.memberId)
    }

    @Test
    fun `차단이 양방향으로 옮겨진다`() {
        val entity = BlockEntity(id = 1L, blockerId = 2L, blockedId = 3L, createdAt = createdAt)
        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals(2L, domain.blockerId)
        assertEquals(3L, domain.blockedId)
        assertEquals(createdAt, domain.createdAt)

        val newEntity = Block.create(blockerId = 4L, blockedId = 5L).toNewEntity()
        assertNull(newEntity.id)
        assertEquals(4L, newEntity.blockerId)
        assertEquals(5L, newEntity.blockedId)
    }

    @Test
    fun `신고가 새 엔티티로 필드마다 옮겨진다`() {
        val entity = Report.create(
            reporterId = 1L,
            targetType = ReportTargetType.COMMENT,
            targetId = 2L,
            reason = ReportReason.OTHER,
            detail = "사유",
        ).toNewEntity()

        assertNull(entity.id)
        assertEquals(1L, entity.reporterId)
        assertEquals(ReportTargetType.COMMENT, entity.targetType)
        assertEquals(2L, entity.targetId)
        assertEquals(ReportReason.OTHER, entity.reason)
        assertEquals("사유", entity.detail)
    }

    @Test
    fun `상세 사유가 null인 신고도 옮겨진다`() {
        val entity = Report.create(
            reporterId = 1L, targetType = ReportTargetType.MEMBER, targetId = 2L,
            reason = ReportReason.SPAM, detail = null,
        ).toNewEntity()

        assertNull(entity.detail)
    }
}
