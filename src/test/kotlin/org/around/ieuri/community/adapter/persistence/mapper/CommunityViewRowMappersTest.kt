package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.conversation.provided.dto.ConversationSpotQaOriginView
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityViewRowMappersTest {

    private val createdAt = OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC)
    private val created = Instant.parse("2026-10-05T00:00:00Z")

    @Test
    fun `글 행은 세 카운터를 섞지 않고 소유 여부는 조회자와 작성자로 정한다`() {
        val row = ResultRow.createAndFillValues(
            mapOf(
                PostTable.id to 1L,
                PostTable.title to "제목",
                PostTable.body to "본문",
                PostTable.authorId to 7L,
                PostTable.likeCount to 11,
                PostTable.commentCount to 12,
                PostTable.viewCount to 13,
                PostTable.hidden to true,
                PostTable.edited to false,
                PostTable.createdAt to createdAt,
            ),
        )
        val view = row.toPostView(viewerId = 7)

        assertEquals(1L, view.id)
        assertEquals("제목", view.title)
        assertEquals("본문", view.body)
        assertEquals(7L, view.authorId)
        assertEquals(11, view.likeCount)
        assertEquals(12, view.commentCount)
        assertEquals(13, view.viewCount)
        assertTrue(view.ownedByViewer)
        assertEquals(true, view.hidden)
        assertEquals(false, view.edited)
        assertEquals(created, view.createdAt)
        assertFalse(row.toPostView(viewerId = 8).ownedByViewer)
    }

    @Test
    fun `댓글 행은 작성자 표시를 비워 두고 부모가 없으면 null이다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                CommentTable.id to 2L,
                CommentTable.parentCommentId to null,
                CommentTable.body to "댓글",
                CommentTable.authorId to 9L,
                CommentTable.hidden to false,
                CommentTable.createdAt to createdAt,
            ),
        ).toCommentView(viewerId = 7)

        assertEquals(2L, view.id)
        assertNull(view.parentCommentId)
        assertEquals("댓글", view.body)
        assertEquals(9L, view.author.id)
        assertEquals("", view.author.nickname)
        assertNull(view.author.profileImageUrl)
        assertFalse(view.ownedByViewer)
        assertEquals(false, view.hidden)
        assertEquals(created, view.createdAt)
    }

    @Test
    fun `질문 행은 장소·작성자·답변 수를 섞지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                SpotQuestionTable.id to 3L,
                SpotQuestionTable.catalogId to 31L,
                SpotQuestionTable.body to "질문",
                SpotQuestionTable.authorId to 32L,
                SpotQuestionTable.resolved to true,
                SpotQuestionTable.answerCount to 33,
                SpotQuestionTable.hidden to false,
                SpotQuestionTable.createdAt to createdAt,
            ),
        ).toSpotQuestionView(viewerId = 32)

        assertEquals(3L, view.id)
        assertEquals(31L, view.catalogId)
        assertEquals("질문", view.body)
        assertEquals(32L, view.authorId)
        assertEquals(true, view.resolved)
        assertEquals(33, view.answerCount)
        assertTrue(view.ownedByViewer)
        assertEquals(false, view.hidden)
        assertEquals(created, view.createdAt)
    }

    @Test
    fun `답변·답글 행은 부모 id와 작성자를 섞지 않는다`() {
        val answer = ResultRow.createAndFillValues(
            mapOf(
                SpotAnswerTable.id to 4L,
                SpotAnswerTable.questionId to 41L,
                SpotAnswerTable.body to "답변",
                SpotAnswerTable.authorId to 42L,
                SpotAnswerTable.hidden to true,
                SpotAnswerTable.createdAt to createdAt,
            ),
        ).toSpotAnswerView(viewerId = 7)
        val reply = ResultRow.createAndFillValues(
            mapOf(
                SpotAnswerReplyTable.id to 5L,
                SpotAnswerReplyTable.answerId to 51L,
                SpotAnswerReplyTable.body to "답글",
                SpotAnswerReplyTable.authorId to 52L,
                SpotAnswerReplyTable.hidden to false,
                SpotAnswerReplyTable.createdAt to createdAt,
            ),
        ).toSpotAnswerReplyView(viewerId = 52)

        assertEquals(4L, answer.id)
        assertEquals(41L, answer.questionId)
        assertEquals("답변", answer.body)
        assertEquals(42L, answer.authorId)
        assertFalse(answer.ownedByViewer)
        assertEquals(true, answer.hidden)
        assertEquals(5L, reply.id)
        assertEquals(51L, reply.answerId)
        assertEquals("답글", reply.body)
        assertEquals(52L, reply.authorId)
        assertTrue(reply.ownedByViewer)
        assertEquals(false, reply.hidden)
        assertEquals(created, reply.createdAt)
    }

    @Test
    fun `메시지 행은 조회자를 함께 담고 사진·인용이 없으면 null이다`() {
        val origin = ConversationSpotQaOriginView(1, "남산", 2, "질문", 3, "답변")
        val row = mapOf(
            MessageTable.id to 6L,
            MessageTable.senderId to 61L,
            MessageTable.createdAt to createdAt,
        )
        val withPhoto = ResultRow.createAndFillValues(row + (MessageTable.photoObjectKey to "images/community/61/k")).toMessageView(viewerId = 61, decryptedBody = "평문", spotQaOrigin = origin)
        val bare = ResultRow.createAndFillValues(row + (MessageTable.photoObjectKey to null)).toMessageView(viewerId = 7, decryptedBody = "", spotQaOrigin = null)

        assertEquals(6L, withPhoto.id)
        assertEquals("평문", withPhoto.body)
        assertEquals(61L, withPhoto.senderId)
        assertTrue(withPhoto.ownedByViewer)
        assertEquals(61L, withPhoto.viewerId)
        assertEquals(created, withPhoto.createdAt)
        assertEquals("images/community/61/k", withPhoto.photoKey)
        assertEquals(origin, withPhoto.spotQaOrigin)
        assertFalse(bare.ownedByViewer)
        assertEquals(7L, bare.viewerId)
        assertNull(bare.photoKey)
        assertNull(bare.spotQaOrigin)
    }

    @Test
    fun `인용 행은 질문·답변 발췌와 id를 섞지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                MessageQuoteTable.contextId to 71L,
                MessageQuoteTable.titleSnapshot to "남산",
                MessageQuoteTable.parentId to 72L,
                MessageQuoteTable.targetId to 73L,
            ),
        ).toSpotQaOriginView(decryptedQuestionExcerpt = "질문 발췌", decryptedAnswerExcerpt = "답변 발췌")

        assertEquals(71L, view.catalogId)
        assertEquals("남산", view.spotTitle)
        assertEquals(72L, view.questionId)
        assertEquals("질문 발췌", view.questionBodySnapshot)
        assertEquals(73L, view.answerId)
        assertEquals("답변 발췌", view.answerBodySnapshot)
    }
}
