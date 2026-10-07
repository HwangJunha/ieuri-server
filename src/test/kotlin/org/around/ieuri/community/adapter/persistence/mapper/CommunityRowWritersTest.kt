package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.BlockTable
import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageReportTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.adapter.persistence.table.PostSpotTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.ReportTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.domain.block.Block
import org.around.ieuri.community.domain.comment.Comment
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.SpotQaOrigin
import org.around.ieuri.community.domain.messagereport.MessageReport
import org.around.ieuri.community.domain.photo.PhotoObjectKey
import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.SpotPoint
import org.around.ieuri.community.domain.question.SpotAnswer
import org.around.ieuri.community.domain.question.SpotAnswerReply
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.ReportReason
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 도메인 → 행 쓰기. 같은 타입 열끼리 뒤바뀌지 않는지 필드마다 다른 값으로 본다. 쓴 값은 내부 API인 `values`로만 읽힌다. */
@OptIn(InternalApi::class)
class CommunityRowWritersTest {

    private fun row(table: Table) = InsertStatement<Number>(table)

    private val encrypt: (String) -> String = { "enc:$it" }

    @Test
    fun `글 행은 작성자·제목·본문·수정 여부를 각 열에 쓴다`() {
        val row = row(PostTable)
        Post(id = null, authorId = 1L, title = "제목", body = "본문", edited = true, hidden = false, deletedAt = null).writeTo(row)

        assertEquals(1L, row.values[PostTable.authorId])
        assertEquals("제목", row.values[PostTable.title])
        assertEquals("본문", row.values[PostTable.body])
        assertEquals(true, row.values[PostTable.edited])
    }

    @Test
    fun `글 수정은 작성자 열을 쓰지 않는다`() {
        val row = row(PostTable)
        Post(id = 9L, authorId = 1L, title = "제목", body = "본문", edited = true, hidden = false, deletedAt = null).writeContentTo(row)

        assertEquals(false, PostTable.authorId in row)
        assertEquals("제목", row.values[PostTable.title])
        assertEquals("본문", row.values[PostTable.body])
        assertEquals(true, row.values[PostTable.edited])
    }

    @Test
    fun `장소 행은 경도를 map_x에, 위도를 map_y에 쓴다`() {
        val row = row(PostSpotTable)
        PostSpot(catalogId = 2L, point = SpotPoint(longitude = 127.5, latitude = 37.5)).writeTo(row, postId = 3L)

        assertEquals(3L, row.values[PostSpotTable.postId])
        assertEquals(2L, row.values[PostSpotTable.catalogId])
        assertEquals(127.5, row.values[PostSpotTable.mapX])
        assertEquals(37.5, row.values[PostSpotTable.mapY])
    }

    @Test
    fun `댓글 행은 글·부모 댓글·작성자를 서로 바꾸지 않는다`() {
        val row = row(CommentTable)
        Comment(id = null, postId = 1L, parentCommentId = 2L, authorId = 3L, body = "댓글", hidden = false, deletedAt = null).writeTo(row)

        assertEquals(1L, row.values[CommentTable.postId])
        assertEquals(2L, row.values[CommentTable.parentCommentId])
        assertEquals(3L, row.values[CommentTable.authorId])
        assertEquals("댓글", row.values[CommentTable.body])
    }

    @Test
    fun `최상위 댓글은 부모 열이 null이다`() {
        val row = row(CommentTable)
        Comment(id = null, postId = 1L, parentCommentId = null, authorId = 3L, body = "댓글", hidden = false, deletedAt = null).writeTo(row)

        assertNull(row.values[CommentTable.parentCommentId])
    }

    @Test
    fun `질문 행은 장소·작성자·본문·해결 여부를 각 열에 쓴다`() {
        val row = row(SpotQuestionTable)
        SpotQuestion(id = null, catalogId = 1L, authorId = 2L, body = "질문", resolved = true, hidden = false, deletedAt = null).writeTo(row)

        assertEquals(1L, row.values[SpotQuestionTable.catalogId])
        assertEquals(2L, row.values[SpotQuestionTable.authorId])
        assertEquals("질문", row.values[SpotQuestionTable.body])
        assertEquals(true, row.values[SpotQuestionTable.resolved])
    }

    @Test
    fun `답변 행은 질문·장소·작성자를 서로 바꾸지 않는다`() {
        val row = row(SpotAnswerTable)
        SpotAnswer(id = null, questionId = 1L, catalogId = 2L, authorId = 3L, body = "답변", hidden = false, deletedAt = null).writeTo(row)

        assertEquals(1L, row.values[SpotAnswerTable.questionId])
        assertEquals(2L, row.values[SpotAnswerTable.catalogId])
        assertEquals(3L, row.values[SpotAnswerTable.authorId])
        assertEquals("답변", row.values[SpotAnswerTable.body])
    }

    @Test
    fun `답글 행은 답변·장소·작성자를 서로 바꾸지 않는다`() {
        val row = row(SpotAnswerReplyTable)
        SpotAnswerReply(id = null, answerId = 1L, catalogId = 2L, authorId = 3L, body = "답글", hidden = false, deletedAt = null).writeTo(row)

        assertEquals(1L, row.values[SpotAnswerReplyTable.answerId])
        assertEquals(2L, row.values[SpotAnswerReplyTable.catalogId])
        assertEquals(3L, row.values[SpotAnswerReplyTable.authorId])
        assertEquals("답글", row.values[SpotAnswerReplyTable.body])
    }

    @Test
    fun `대화 행은 두 참여자를 바꾸지 않는다`() {
        val row = row(ConversationTable)
        Conversation(id = null, memberLow = 1L, memberHigh = 2L, lastMessageAt = null, lastReadMessageIdLow = null, lastReadMessageIdHigh = null).writeTo(row)

        assertEquals(1L, row.values[ConversationTable.memberLow])
        assertEquals(2L, row.values[ConversationTable.memberHigh])
    }

    @Test
    fun `메시지 행은 본문을 암호화하고 대화·발신자·사진 키를 각 열에 쓴다`() {
        val key = requireNotNull(PhotoObjectKey.parse("images/community/2/${UUID.randomUUID()}"))
        val row = row(MessageTable)
        Message(id = null, conversationId = 1L, senderId = 2L, body = "안녕", photoKey = key, spotQaOrigin = null, createdAt = null).writeTo(row, encrypt)

        assertEquals(1L, row.values[MessageTable.conversationId])
        assertEquals(2L, row.values[MessageTable.senderId])
        assertEquals("enc:안녕", row.values[MessageTable.bodyEncrypted])
        assertEquals(key.value, row.values[MessageTable.photoObjectKey])
    }

    @Test
    fun `사진 없는 메시지는 사진 키 열이 null이다`() {
        val row = row(MessageTable)
        Message(id = null, conversationId = 1L, senderId = 2L, body = "안녕", photoKey = null, spotQaOrigin = null, createdAt = null).writeTo(row, encrypt)

        assertNull(row.values[MessageTable.photoObjectKey])
    }

    @Test
    fun `인용 행은 답변을 target, 장소를 context, 질문을 parent에 쓰고 발췌를 암호화한다`() {
        val origin = SpotQaOrigin(catalogId = 1L, spotTitle = "경복궁", questionId = 2L, questionExcerpt = "질문 발췌", answerId = 3L, answerExcerpt = "답변 발췌")
        val row = row(MessageQuoteTable)
        origin.writeQuoteTo(row, messageId = 4L, encrypt = encrypt)

        assertEquals(4L, row.values[MessageQuoteTable.messageId])
        assertEquals("SPOT_ANSWER", row.values[MessageQuoteTable.quoteType])
        assertEquals(3L, row.values[MessageQuoteTable.targetId])
        assertEquals(1L, row.values[MessageQuoteTable.contextId])
        assertEquals(2L, row.values[MessageQuoteTable.parentId])
        assertEquals("경복궁", row.values[MessageQuoteTable.titleSnapshot])
        assertEquals("enc:질문 발췌", row.values[MessageQuoteTable.primaryExcerptEncrypted])
        assertEquals("enc:답변 발췌", row.values[MessageQuoteTable.secondaryExcerptEncrypted])
    }

    @Test
    fun `차단 행은 차단한 쪽과 당한 쪽을 바꾸지 않는다`() {
        val row = row(BlockTable)
        Block(blockerId = 1L, blockedId = 2L).writeTo(row)

        assertEquals(1L, row.values[BlockTable.blockerId])
        assertEquals(2L, row.values[BlockTable.blockedId])
    }

    @Test
    fun `신고 행은 신고자·대상·사유를 각 열에 쓴다`() {
        val row = row(ReportTable)
        Report(reporterId = 1L, targetType = ReportTargetType.SPOT_ANSWER, targetId = 2L, reason = ReportReason.OTHER, detail = "상세").writeTo(row)

        assertEquals(1L, row.values[ReportTable.reporterId])
        assertEquals("SPOT_ANSWER", row.values[ReportTable.targetType])
        assertEquals(2L, row.values[ReportTable.targetId])
        assertEquals("OTHER", row.values[ReportTable.reason])
        assertEquals("상세", row.values[ReportTable.detail])
    }

    @Test
    fun `메시지 신고 행은 신고자·대화·사유를 각 열에 쓴다`() {
        val row = row(MessageReportTable)
        MessageReport(id = null, reporterId = 1L, conversationId = 2L, messageIds = listOf(3L), reason = ReportReason.SPAM, detail = null).writeTo(row)

        assertEquals(1L, row.values[MessageReportTable.reporterId])
        assertEquals(2L, row.values[MessageReportTable.conversationId])
        assertEquals("SPAM", row.values[MessageReportTable.reason])
        assertNull(row.values[MessageReportTable.detail])
    }
}
