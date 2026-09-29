package org.around.ieuri.community.infrastructure

import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.ResolveSpotQuestionInput
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GraphQL 입력 → Command 변환(SPEC-0026). 필드마다 다른 값을 쓴다. */
class SpotQaInputMapperTest {

    @Test
    fun `질문 작성 입력이 필드별로 옮겨진다`() {
        val input = CreateSpotQuestionInput(
            catalogId = 11L,
            body = "웨이팅 있나요?",
            photoKeys = listOf("images/community/7/a", "images/community/7/b"),
        )

        val command = input.toCommand()

        assertEquals(11L, command.catalogId)
        assertEquals("웨이팅 있나요?", command.body)
        assertEquals(listOf("images/community/7/a", "images/community/7/b"), command.photoKeys)
    }

    @Test
    fun `사진 없는 질문 작성도 변환된다`() {
        val command = CreateSpotQuestionInput(catalogId = 11L, body = "질문", photoKeys = emptyList())
            .toCommand()

        assertTrue(command.photoKeys.isEmpty())
    }

    @Test
    fun `질문 삭제 입력이 옮겨진다`() {
        assertEquals(23L, DeleteSpotQuestionInput(questionId = 23L).toCommand().questionId)
    }

    @Test
    fun `해결 표시 입력이 필드별로 옮겨진다`() {
        val command = ResolveSpotQuestionInput(questionId = 34L, resolved = true).toCommand()

        assertEquals(34L, command.questionId)
        assertEquals(true, command.resolved)
    }

    @Test
    fun `되돌리기 입력의 resolved가 false로 옮겨진다`() {
        val command = ResolveSpotQuestionInput(questionId = 34L, resolved = false).toCommand()

        assertEquals(34L, command.questionId)
        assertEquals(false, command.resolved)
    }

    @Test
    fun `답변 작성 입력이 필드별로 옮겨진다`() {
        val input = CreateSpotAnswerInput(
            questionId = 45L,
            body = "지금은 없어요",
            photoKeys = listOf("images/community/7/c"),
        )

        val command = input.toCommand()

        assertEquals(45L, command.questionId)
        assertEquals("지금은 없어요", command.body)
        assertEquals(listOf("images/community/7/c"), command.photoKeys)
    }

    @Test
    fun `답변 삭제 입력이 옮겨진다`() {
        assertEquals(56L, DeleteSpotAnswerInput(answerId = 56L).toCommand().answerId)
    }

    @Test
    fun `답글 작성 입력이 필드별로 옮겨진다`() {
        val input = CreateSpotAnswerReplyInput(
            parentAnswerId = 67L,
            body = "감사합니다",
            photoKeys = listOf("images/community/7/d"),
        )

        val command = input.toCommand()

        assertEquals(67L, command.parentAnswerId)
        assertEquals("감사합니다", command.body)
        assertEquals(listOf("images/community/7/d"), command.photoKeys)
    }

    @Test
    fun `답글 삭제 입력이 옮겨진다`() {
        assertEquals(78L, DeleteSpotAnswerReplyInput(replyId = 78L).toCommand().replyId)
    }
}
