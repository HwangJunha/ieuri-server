package org.around.ieuri.community.domain.post

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostTest {

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val post = Post(id = 1, authorId = 7, title = "제목", body = "본문", edited = false, hidden = false, deletedAt = null)

    @Test
    fun `새 글은 제목·본문을 트리밍하고 편집·숨김·삭제 표시가 없다`() {
        val written = Post.write(authorId = 7, title = "  제목  ", body = "  본문  ")

        assertNull(written.id)
        assertEquals("제목", written.title)
        assertEquals("본문", written.body)
        assertEquals(false, written.edited)
        assertEquals(false, written.hidden)
        assertNull(written.deletedAt)
    }

    @Test
    fun `제목과 본문은 비거나 길면 각자의 오류다`() {
        assertFailsWith<PostTitleRequiredException> { Post.write(7, "   ", "본문") }
        assertFailsWith<PostTitleLengthInvalidException> { Post.write(7, "가".repeat(Post.TITLE_MAX_LENGTH + 1), "본문") }
        assertFailsWith<PostBodyRequiredException> { Post.write(7, "제목", " ") }
        assertFailsWith<PostBodyLengthInvalidException> { Post.write(7, "제목", "가".repeat(Post.BODY_MAX_LENGTH + 1)) }
    }

    @Test
    fun `상한 정확히는 통과한다`() {
        Post.write(7, "가".repeat(Post.TITLE_MAX_LENGTH), "가".repeat(Post.BODY_MAX_LENGTH))
    }

    @Test
    fun `편집은 작성자만 하고 편집 표시가 붙는다`() {
        val edited = post.edit(memberId = 7, title = "새 제목", body = "새 본문")

        assertEquals("새 제목", edited.title)
        assertEquals("새 본문", edited.body)
        assertTrue(edited.edited)
        assertFailsWith<NotPostOwnerException> { post.edit(memberId = 8, title = "t", body = "b") }
    }

    @Test
    fun `숨겨진 글은 작성자라도 고칠 수 없다`() {
        assertFailsWith<HiddenPostNotEditableException> { post.copy(hidden = true).edit(7, "t", "b") }
    }

    @Test
    fun `삭제는 작성자만 하고 삭제 시각이 남는다`() {
        assertEquals(now, post.delete(memberId = 7, now = now).deletedAt)
        assertFailsWith<NotPostOwnerException> { post.delete(memberId = 8, now = now) }
    }
}
