package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.ConfirmPostImageUploadsInput
import org.around.ieuri.common.graphql.generated.types.CreateCommentInput
import org.around.ieuri.common.graphql.generated.types.CreatePostImageUploadUrlsInput
import org.around.ieuri.common.graphql.generated.types.CreatePostInput
import org.around.ieuri.common.graphql.generated.types.EditPostInput
import org.around.ieuri.common.graphql.generated.types.PostImageUploadRequest
import org.around.ieuri.common.graphql.generated.types.ReportContentInput
import org.around.ieuri.community.application.shared.dto.PostImageConfirmFailure
import org.around.ieuri.community.application.shared.dto.PostImageUploadUrlView
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.ReportReason
import org.around.ieuri.community.domain.shared.TargetCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.around.ieuri.common.graphql.generated.types.ReportReason as GqlReportReason
import org.around.ieuri.common.graphql.generated.types.ReportTargetType as GqlReportTargetType
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/** GraphQL 입력 ↔ 커맨드·응답 변환. 필드마다 다른 값을 준다. */
class CommunityInputMappersTest {

    @Test
    fun `글 작성 입력이 커맨드로 옮겨진다`() {
        val command = CreatePostInput(
            category = GqlTargetCategory.RESTAURANT,
            catalogId = 11L,
            tags = listOf("서울", "맛집"),
            title = "제목",
            body = "본문",
            photoKeys = listOf("k1", "k2"),
        ).toCommand()

        assertEquals(TargetCategory.RESTAURANT, command.category)
        assertEquals(11L, command.catalogId)
        assertEquals(listOf("서울", "맛집"), command.tags)
        assertEquals("제목", command.title)
        assertEquals("본문", command.body)
        assertEquals(listOf("k1", "k2"), command.photoKeys)
    }

    @Test
    fun `글 작성에서 장소를 생략하면 null이다`() {
        assertNull(CreatePostInput(GqlTargetCategory.SHOPPING, null, emptyList(), "t", "b", emptyList()).toCommand().catalogId)
    }

    @Test
    fun `글 수정 입력이 커맨드로 옮겨진다`() {
        val command = EditPostInput(
            postId = 21L,
            title = "새 제목",
            body = "새 본문",
            photoKeys = listOf("k"),
            category = GqlTargetCategory.LODGING,
            changeSpot = true,
            catalogId = 22L,
            tags = listOf("t"),
        ).toCommand()

        assertEquals(21L, command.postId)
        assertEquals("새 제목", command.title)
        assertEquals("새 본문", command.body)
        assertEquals(listOf("k"), command.photoKeys)
        assertEquals(TargetCategory.LODGING, command.category)
        assertEquals(true, command.changeSpot)
        assertEquals(22L, command.catalogId)
        assertEquals(listOf("t"), command.tags)
    }

    @Test
    fun `글 수정에서 생략한 항목은 null이고 changeSpot이 없으면 장소를 건드리지 않는다`() {
        val command = EditPostInput(postId = 1L, title = "t", body = "b", changeSpot = null).toCommand()

        assertNull(command.photoKeys)
        assertNull(command.category)
        assertFalse(command.changeSpot)
        assertNull(command.catalogId)
        assertNull(command.tags)
    }

    @Test
    fun `댓글 입력이 커맨드로 옮겨지고 부모가 없으면 null이다`() {
        val reply = CreateCommentInput(postId = 31L, body = "댓글", parentCommentId = 32L).toCommand()
        val top = CreateCommentInput(postId = 33L, body = "c").toCommand()

        assertEquals(31L, reply.postId)
        assertEquals("댓글", reply.body)
        assertEquals(32L, reply.parentCommentId)
        assertNull(top.parentCommentId)
    }

    @Test
    fun `신고 입력이 커맨드로 옮겨지고 상세 사유가 없으면 null이다`() {
        val command = ReportContentInput(GqlReportTargetType.SPOT_ANSWER, targetId = 41L, reason = GqlReportReason.ABUSE, detail = "욕설").toCommand()

        assertEquals(ReportTargetType.SPOT_ANSWER, command.targetType)
        assertEquals(41L, command.targetId)
        assertEquals(ReportReason.ABUSE, command.reason)
        assertEquals("욕설", command.detail)
        assertNull(ReportContentInput(GqlReportTargetType.POST, 1L, GqlReportReason.SPAM).toCommand().detail)
    }

    @Test
    fun `사진 업로드 요청은 형식과 크기를 섞지 않는다`() {
        val command = CreatePostImageUploadUrlsInput(
            images = listOf(PostImageUploadRequest("image/png", 100), PostImageUploadRequest("image/jpeg", 200)),
        ).toCommand()

        assertEquals(listOf("image/png", "image/jpeg"), command.images.map { it.contentType })
        assertEquals(listOf(100L, 200L), command.images.map { it.contentLength })
        assertEquals(listOf("a", "b"), ConfirmPostImageUploadsInput(objectKeys = listOf("a", "b")).toCommand().objectKeys)
    }

    @Test
    fun `업로드 URL과 실패 응답은 각 필드로 가고 만료 시각은 ISO-8601이다`() {
        val url = PostImageUploadUrlView(uploadUrl = "https://r2/put", objectKey = "pending/community/1/u", expiresAt = Instant.parse("2026-10-05T00:30:00Z")).toResponse()
        val failure = PostImageConfirmFailure(objectKey = "pending/community/1/x", code = "PHOTO_NOT_UPLOADED").toResponse()

        assertEquals("https://r2/put", url.uploadUrl)
        assertEquals("pending/community/1/u", url.objectKey)
        assertEquals("2026-10-05T00:30:00Z", url.expiresAt)
        assertEquals("pending/community/1/x", failure.objectKey)
        assertEquals("PHOTO_NOT_UPLOADED", failure.code)
    }
}
