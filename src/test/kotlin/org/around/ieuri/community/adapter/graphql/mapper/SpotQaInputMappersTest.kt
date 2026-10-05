package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionInput
import kotlin.test.Test
import kotlin.test.assertEquals

/** 장소 Q&A 입력 → 커맨드. 필드마다 다른 값을 준다. */
class SpotQaInputMappersTest {

    @Test
    fun `질문 입력이 커맨드로 옮겨진다`() {
        val command = CreateSpotQuestionInput(catalogId = 11L, body = "질문", photoKeys = listOf("q1")).toCommand()

        assertEquals(11L, command.catalogId)
        assertEquals("질문", command.body)
        assertEquals(listOf("q1"), command.photoKeys)
    }

    @Test
    fun `답변 입력이 커맨드로 옮겨진다`() {
        val command = CreateSpotAnswerInput(questionId = 21L, body = "답변", photoKeys = listOf("a1", "a2")).toCommand()

        assertEquals(21L, command.questionId)
        assertEquals("답변", command.body)
        assertEquals(listOf("a1", "a2"), command.photoKeys)
    }

    @Test
    fun `답글 입력의 대상 답변 id가 옮겨진다`() {
        val command = CreateSpotAnswerReplyInput(parentAnswerId = 31L, body = "답글", photoKeys = emptyList()).toCommand()

        assertEquals(31L, command.parentAnswerId)
        assertEquals("답글", command.body)
        assertEquals(emptyList(), command.photoKeys)
    }
}
