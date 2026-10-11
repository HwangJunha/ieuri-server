package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.test.Test
import kotlin.test.assertEquals

/** 다른 애그리게이트가 조회 유스케이스로 읽는 읽기 모델의 변환(POL-033). 같은 타입 열끼리 뒤바뀌지 않게 값을 다르게 준다. */
class CrossAggregateViewRowMappersTest {

    @Test
    fun `답변-질문 조인 행에서 답변과 질문의 id·본문이 섞이지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                SpotAnswerTable.id to 21L,
                SpotAnswerTable.authorId to 9L,
                SpotAnswerTable.body to "답변 본문",
                SpotAnswerTable.catalogId to 343259L,
                SpotQuestionTable.id to 4L,
                SpotQuestionTable.body to "질문 본문",
            ),
        ).toSpotAnswerOriginView()

        assertEquals(21L, view.answerId)
        assertEquals(9L, view.answerAuthorId)
        assertEquals("답변 본문", view.answerBody)
        assertEquals(4L, view.questionId)
        assertEquals("질문 본문", view.questionBody)
        assertEquals(343259L, view.catalogId)
    }

    @Test
    fun `메시지 행에서 메시지·대화·보낸 사람 id가 섞이지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(MessageTable.id to 501L, MessageTable.conversationId to 5L, MessageTable.senderId to 9L),
        ).toMessageOwnerView()

        assertEquals(501L, view.messageId)
        assertEquals(5L, view.conversationId)
        assertEquals(9L, view.senderId)
    }

    @Test
    fun `대화 행에서 두 참여자가 바뀌지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(ConversationTable.id to 5L, ConversationTable.memberLow to 7L, ConversationTable.memberHigh to 9L),
        ).toConversationParticipantsView()

        assertEquals(5L, view.conversationId)
        assertEquals(7L, view.memberLow)
        assertEquals(9L, view.memberHigh)
    }
}
