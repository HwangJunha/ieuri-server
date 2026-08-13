package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.SpotPoint
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 글 도메인 불변식(R1~R4). 본문 길이 경계와 사진 개수·소유권을 고정한다. */
class PostTest {

    private val authorId = 7L
    private val spotId = 42L
    private val catalogId = 420L

    private fun confirmedKey(memberId: Long = authorId): PhotoObjectKey =
        PhotoObjectKey.issue(memberId, UUID.randomUUID()).toConfirmed()

    private fun create(
        title: String = "제목",
        body: String = "본문",
        photoKeys: List<PhotoObjectKey> = emptyList(),
    ) = Post.create(
        category = TargetCategory.TOURIST_SPOT,
        spotId = spotId,
        catalogId = catalogId,
        spotPoint = SpotPoint(127.0, 37.5),
        authorId = authorId,
        tags = emptyList(),
        title = title,
        body = body,
        photoKeys = photoKeys,
    )

    @Test
    fun `제목이 1자면 생성된다`() {
        assertEquals("가", create(title = "가").title)
    }

    @Test
    fun `제목이 50자면 생성된다`() {
        assertEquals(50, create(title = "가".repeat(50)).title.length)
    }

    @Test
    fun `제목이 51자면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(title = "가".repeat(51)) }
    }

    @Test
    fun `공백만 있는 제목은 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(title = "   ") }
    }

    @Test
    fun `제목 앞뒤 공백은 제거된다`() {
        assertEquals("제목", create(title = "  제목  ").title)
    }

    @Test
    fun `본문이 1자면 생성된다`() {
        assertEquals("한", create(body = "한").body)
    }

    @Test
    fun `본문이 500자면 생성된다`() {
        val body = "가".repeat(500)
        assertEquals(500, create(body = body).body.length)
    }

    @Test
    fun `본문이 501자면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(body = "가".repeat(501)) }
    }

    @Test
    fun `공백만 있는 본문은 트리밍 후 빈 문자열이라 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(body = " ".repeat(500)) }
    }

    @Test
    fun `본문 앞뒤 공백은 제거된다`() {
        assertEquals("본문", create(body = "  본문  ").body)
    }

    @Test
    fun `사진 5장은 허용된다`() {
        val keys = List(5) { confirmedKey() }
        assertEquals(5, create(photoKeys = keys).photoKeys.size)
    }

    @Test
    fun `사진 6장은 거부된다`() {
        val keys = List(6) { confirmedKey() }
        assertFailsWith<IllegalArgumentException> { create(photoKeys = keys) }
    }

    @Test
    fun `사진이 없어도 생성된다 - 사진은 선택이다`() {
        assertTrue(create(photoKeys = emptyList()).photoKeys.isEmpty())
    }

    @Test
    fun `타인에게 발급된 사진 키는 거부된다`() {
        val othersKey = confirmedKey(memberId = authorId + 1)
        assertFailsWith<IllegalArgumentException> { create(photoKeys = listOf(othersKey)) }
    }

    @Test
    fun `확인을 마치지 않은 사진 키는 거부된다`() {
        val pending = PhotoObjectKey.issue(authorId, UUID.randomUUID())
        assertFailsWith<IllegalArgumentException> { create(photoKeys = listOf(pending)) }
    }

    @Test
    fun `작성자는 자신의 글을 삭제할 수 있다`() {
        val post = create()
        assertTrue(post.deleteBy(authorId, Instant.EPOCH))
        assertTrue(post.deleted)
    }

    @Test
    fun `타인은 글을 삭제할 수 없고 상태도 바뀌지 않는다`() {
        val post = create()
        assertFalse(post.deleteBy(authorId + 1, Instant.EPOCH))
        assertFalse(post.deleted)
    }

    @Test
    fun `작성자는 제목과 본문을 수정할 수 있다`() {
        val post = create()
        assertTrue(post.editBy(authorId, "새 제목", "새 본문"))
        assertEquals("새 제목", post.title)
        assertEquals("새 본문", post.body)
        assertTrue(post.edited)
    }

    @Test
    fun `수정 전에는 edited가 false다`() {
        assertFalse(create().edited)
    }

    @Test
    fun `타인은 수정할 수 없고 내용도 그대로다`() {
        val post = create(body = "원래 본문")
        assertFalse(post.editBy(authorId + 1, "탈취 제목", "탈취 본문"))
        assertEquals("원래 본문", post.body)
        assertFalse(post.edited)
    }

    @Test
    fun `숨겨진 글은 작성자도 수정할 수 없다 - 신고 기록이 대상을 가리키기 때문이다`() {
        val post = create(body = "신고당한 본문")
        post.hide()

        assertFalse(post.editBy(authorId, "무해한 제목", "무해한 본문"))
        assertEquals("신고당한 본문", post.body)
        assertFalse(post.edited)
    }

    @Test
    fun `삭제된 글은 수정할 수 없다`() {
        val post = create()
        post.deleteBy(authorId, Instant.EPOCH)
        assertFalse(post.editBy(authorId, "제목", "본문"))
    }

    @Test
    fun `수정도 생성과 같은 길이 규칙을 적용한다`() {
        val post = create()
        assertFailsWith<IllegalArgumentException> { post.editBy(authorId, "", "본문") }
        assertFailsWith<IllegalArgumentException> { post.editBy(authorId, "가".repeat(51), "본문") }
        assertFailsWith<IllegalArgumentException> { post.editBy(authorId, "제목", "   ") }
        assertFailsWith<IllegalArgumentException> { post.editBy(authorId, "제목", "가".repeat(501)) }
    }

    @Test
    fun `수정 시 앞뒤 공백은 제거된다`() {
        val post = create()
        post.editBy(authorId, "  제목  ", "  본문  ")
        assertEquals("제목", post.title)
        assertEquals("본문", post.body)
    }

    @Test
    fun `좋아요 수는 0 아래로 내려가지 않는다`() {
        val post = create()
        post.decreaseLikeCount()
        assertEquals(0, post.likeCount)
    }

    @Test
    fun `댓글 수는 0 아래로 내려가지 않는다`() {
        val post = create()
        post.decreaseCommentCount()
        assertEquals(0, post.commentCount)
    }

    @Test
    fun `숨겨진 글은 작성자 본인에게만 보인다`() {
        val post = create()
        post.hide()
        assertTrue(post.visibleTo(authorId))
        assertFalse(post.visibleTo(authorId + 1))
    }

    @Test
    fun `삭제된 글은 작성자에게도 보이지 않는다`() {
        val post = create()
        post.deleteBy(authorId, Instant.EPOCH)
        assertFalse(post.visibleTo(authorId))
    }

    // ---------- 수정 (카테고리·장소·태그·사진) ----------

    @Test
    fun `카테고리를 바꿀 수 있다`() {
        val post = create()

        post.changeCategory(TargetCategory.RESTAURANT)

        assertEquals(TargetCategory.RESTAURANT, post.category)
    }

    @Test
    fun `장소를 바꾸면 좌표도 함께 바뀐다`() {
        val post = create()

        post.changeSpot(99L, 990L, SpotPoint(128.5, 35.5))

        assertEquals(99L, post.spotId)
        assertEquals(990L, post.catalogId)
        assertEquals(SpotPoint(128.5, 35.5), post.spotPoint)
    }

    @Test
    fun `장소를 뗄 수 있다`() {
        val post = create()

        post.changeSpot(null, null, null)

        assertNull(post.spotId)
        assertNull(post.catalogId)
        assertNull(post.spotPoint)
    }

    @Test
    fun `장소와 좌표는 함께 있거나 함께 없어야 한다`() {
        val post = create()

        // 좌표 없이 장소만 바꾸면 그 글이 지역 피드의 거리순에서 조용히 사라진다.
        assertFailsWith<IllegalArgumentException> { post.changeSpot(99L, 990L, null) }
        assertFailsWith<IllegalArgumentException> { post.changeSpot(null, null, SpotPoint(127.0, 37.5)) }
    }

    @Test
    fun `장소와 카탈로그는 함께 있거나 함께 없어야 한다`() {
        val post = create()

        // 카탈로그가 조회 축이라, 없으면 그 글은 어느 장소 피드에도 나타나지 않는다.
        assertFailsWith<IllegalArgumentException> {
            post.changeSpot(99L, null, SpotPoint(128.5, 35.5))
        }
        assertFailsWith<IllegalArgumentException> { post.changeSpot(null, 990L, null) }
    }

    @Test
    fun `태그를 교체하면 같은 태그는 하나로 본다`() {
        val post = create()

        post.replaceTags(
            listOf(
                PostTag.restore("Seoul", "seoul"),
                PostTag.restore("seoul", "seoul"),
                PostTag.restore("야경", "야경"),
            ),
        )

        // 유저가 "서울"과 "#서울"을 함께 칠 수 있다.
        assertEquals(2, post.tags.size)
        assertEquals(listOf("Seoul", "야경"), post.tags.map { it.name })
    }

    @Test
    fun `태그는 6개부터 거부된다`() {
        val post = create()
        val six = (1..6).map { PostTag.restore("태그$it", "태그$it") }

        assertFailsWith<IllegalArgumentException> { post.replaceTags(six) }
    }

    @Test
    fun `사진을 교체할 수 있다`() {
        val post = create(photoKeys = listOf(confirmedKey()))
        val next = listOf(confirmedKey(), confirmedKey())

        post.replacePhotos(next)

        assertEquals(next, post.photoKeys)
    }

    @Test
    fun `남의 사진 키로는 교체할 수 없다`() {
        val post = create()

        assertFailsWith<IllegalArgumentException> {
            post.replacePhotos(listOf(confirmedKey(memberId = authorId + 1)))
        }
    }

    @Test
    fun `확인을 마치지 않은 키로는 교체할 수 없다`() {
        val post = create()
        val pending = PhotoObjectKey.issue(authorId, UUID.randomUUID())

        assertFailsWith<IllegalArgumentException> { post.replacePhotos(listOf(pending)) }
    }
}
