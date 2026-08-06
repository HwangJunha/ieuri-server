package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.BlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostTagEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPostTag
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
        val key = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val tag = PostTag.restore("야경", "야경")

        // 태그·사진은 별도 테이블이라 **호출자가 읽어 넘긴다** — 엔티티에 컬렉션을 두면
        // 글을 읽을 때마다 자식 조회가 따라붙는다.
        val domain = entity.toDomain(
            category = TargetCategory.TOURIST_SPOT,
            spotId = 9L,
            tags = listOf(tag),
            photoKeys = listOf(key),
        )

        assertEquals(1L, domain.id)
        assertEquals(TargetCategory.TOURIST_SPOT, domain.category)
        assertEquals(9L, domain.spotId)
        assertEquals(listOf(tag), domain.tags)
        assertEquals(listOf(key), domain.photoKeys)
        assertEquals("제목", domain.title)
        assertEquals(3L, domain.authorId)
        assertEquals("본문", domain.body)
        assertEquals(4, domain.likeCount)
        assertEquals(5, domain.commentCount)
        assertEquals(true, domain.hidden)
        assertEquals(deletedAt, domain.deletedAt)
        assertEquals(createdAt, domain.createdAt)
    }

    @Test
    fun `사진 엔티티는 배열 순서대로 position을 받는다`() {
        val keyA = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val keyB = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val domain = Post.create(
            category = TargetCategory.TOURIST_SPOT,
            spotId = 2L, authorId = 3L, tags = emptyList(),
            title = "제목", body = "본문", photoKeys = listOf(keyA, keyB),
        )

        val photos = domain.toPhotoEntities(postId = 1L)

        // 배열 순서가 곧 표시 순서다. 읽을 때의 정렬은 리포지토리 쿼리가 보장한다.
        assertEquals(listOf(keyA.value, keyB.value), photos.map { it.objectKey })
        assertEquals(listOf<Short>(0, 1), photos.map { it.position })
        assertEquals(listOf(1L, 1L), photos.map { it.postId })
    }

    @Test
    fun `사진 키 문자열이 도메인 키로 옮겨진다`() {
        val key = PhotoObjectKey.issue(3L, UUID.randomUUID()).toConfirmed()
        val entity = PostPhotoEntity(postId = 1L, objectKey = key.value, position = 0)

        assertEquals(key, entity.toPhotoKey())
    }

    @Test
    fun `태그 엔티티가 도메인 태그로 옮겨진다`() {
        val entity = PostTagEntity(postId = 1L, name = "Seoul", normalized = "seoul")

        val tag = entity.toPostTag()

        // 표기는 원문 보존, 매칭은 정규화된 값이다.
        assertEquals("Seoul", tag.name)
        assertEquals("seoul", tag.normalized)
    }

    @Test
    fun `nullable 필드가 모두 null인 글도 옮겨진다`() {
        val entity = PostEntity(
            id = 1L, authorId = 3L,
            title = "제목", body = "본문",
            deletedAt = null, createdAt = createdAt, updatedAt = createdAt,
        )

        val domain = entity.toDomain(
            category = TargetCategory.TOURIST_SPOT,
            spotId = null,
            tags = emptyList(),
            photoKeys = emptyList(),
        )

        assertNull(domain.deletedAt)
        // 장소는 선택이라 없는 것이 정상이다.
        assertNull(domain.spotId)
        assertTrue(domain.photoKeys.isEmpty())
        assertTrue(domain.tags.isEmpty())
    }

    @Test
    fun `도메인 글이 새 엔티티로 필드마다 옮겨진다`() {
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
