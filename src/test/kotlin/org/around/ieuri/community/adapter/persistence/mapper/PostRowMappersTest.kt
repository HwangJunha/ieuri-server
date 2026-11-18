package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostRowMappersTest {

    private val deletedAt = OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `글 행의 각 열이 같은 이름의 필드로 간다`() {
        val post = ResultRow.createAndFillValues(
            mapOf(
                PostTable.id to 11L,
                PostTable.authorId to 22L,
                PostTable.title to "제목",
                PostTable.body to "본문",
                PostTable.edited to true,
                PostTable.hidden to false,
                PostTable.deletedAt to deletedAt,
            ),
        ).toPost()

        assertEquals(11L, post.id)
        assertEquals(22L, post.authorId)
        assertEquals("제목", post.title)
        assertEquals("본문", post.body)
        assertEquals(true, post.edited)
        assertEquals(false, post.hidden)
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), post.deletedAt)
    }

    @Test
    fun `편집·숨김 표시는 서로 바뀌지 않고 삭제 시각이 없으면 null이다`() {
        val post = ResultRow.createAndFillValues(
            mapOf(
                PostTable.id to 1L,
                PostTable.authorId to 2L,
                PostTable.title to "t",
                PostTable.body to "b",
                PostTable.edited to false,
                PostTable.hidden to true,
                PostTable.deletedAt to null,
            ),
        ).toPost()

        assertEquals(false, post.edited)
        assertEquals(true, post.hidden)
        assertNull(post.deletedAt)
    }

    @Test
    fun `댓글 행의 각 열이 같은 이름의 필드로 간다`() {
        val comment = ResultRow.createAndFillValues(
            mapOf(
                CommentTable.id to 31L,
                CommentTable.postId to 32L,
                CommentTable.parentCommentId to 33L,
                CommentTable.authorId to 34L,
                CommentTable.body to "댓글",
                CommentTable.hidden to true,
                CommentTable.deletedAt to deletedAt,
            ),
        ).toComment()

        assertEquals(31L, comment.id)
        assertEquals(32L, comment.postId)
        assertEquals(33L, comment.parentCommentId)
        assertEquals(34L, comment.authorId)
        assertEquals("댓글", comment.body)
        assertEquals(true, comment.hidden)
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), comment.deletedAt)
    }

    @Test
    fun `최상위·살아 있는 댓글은 부모와 삭제 시각이 null이다`() {
        val comment = ResultRow.createAndFillValues(
            mapOf(
                CommentTable.id to 1L,
                CommentTable.postId to 2L,
                CommentTable.parentCommentId to null,
                CommentTable.authorId to 3L,
                CommentTable.body to "c",
                CommentTable.hidden to false,
                CommentTable.deletedAt to null,
            ),
        ).toComment()

        assertNull(comment.parentCommentId)
        assertNull(comment.deletedAt)
    }

    @Test
    fun `timestamptz 값은 같은 시점의 UTC 오프셋이다`() {
        val instant = Instant.parse("2026-10-05T01:02:03.456789Z")

        assertEquals(OffsetDateTime.of(2026, 10, 5, 1, 2, 3, 456_789_000, ZoneOffset.UTC), instant.toTimestamptz())
    }
}
