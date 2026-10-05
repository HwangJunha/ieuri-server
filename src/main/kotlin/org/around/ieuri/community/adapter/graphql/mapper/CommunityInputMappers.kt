package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.ConfirmPostImageUploadsInput
import org.around.ieuri.common.graphql.generated.types.CreateCommentInput
import org.around.ieuri.common.graphql.generated.types.CreatePostImageUploadUrlsInput
import org.around.ieuri.common.graphql.generated.types.CreatePostInput
import org.around.ieuri.common.graphql.generated.types.EditPostInput
import org.around.ieuri.common.graphql.generated.types.PostImageUploadFailure
import org.around.ieuri.common.graphql.generated.types.PostImageUploadUrl
import org.around.ieuri.common.graphql.generated.types.ReportContentInput
import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.application.photo.provided.dto.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.photo.provided.dto.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.photo.provided.dto.PostImageUploadRequest
import org.around.ieuri.community.application.post.provided.dto.CreatePostCommand
import org.around.ieuri.community.application.post.provided.dto.EditPostCommand
import org.around.ieuri.community.application.report.provided.ReportContentCommand
import org.around.ieuri.community.application.shared.dto.PostImageConfirmFailure
import org.around.ieuri.community.application.shared.dto.PostImageUploadUrlView
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.ReportReason
import org.around.ieuri.community.domain.shared.TargetCategory

// GraphQL 입력 → 커맨드, 읽기 모델 → GraphQL 타입. 모양만 옮긴다.

fun CreatePostInput.toCommand(): CreatePostCommand = CreatePostCommand(
    category = TargetCategory.valueOf(category.name),
    catalogId = catalogId,
    tags = tags,
    title = title,
    body = body,
    photoKeys = photoKeys,
)

/** `changeSpot`이 없으면 장소를 건드리지 않는다. */
fun EditPostInput.toCommand(): EditPostCommand = EditPostCommand(
    postId = postId,
    title = title,
    body = body,
    photoKeys = photoKeys,
    category = category?.let { TargetCategory.valueOf(it.name) },
    changeSpot = changeSpot ?: false,
    catalogId = catalogId,
    tags = tags,
)

fun CreateCommentInput.toCommand(): CreateCommentCommand = CreateCommentCommand(
    postId = postId,
    body = body,
    parentCommentId = parentCommentId,
)

fun ReportContentInput.toCommand(): ReportContentCommand = ReportContentCommand(
    targetType = ReportTargetType.valueOf(targetType.name),
    targetId = targetId,
    reason = ReportReason.valueOf(reason.name),
    detail = detail,
)

fun CreatePostImageUploadUrlsInput.toCommand(): IssuePostImageUploadUrlsCommand = IssuePostImageUploadUrlsCommand(
    images = images.map { PostImageUploadRequest(contentType = it.contentType, contentLength = it.contentLength.toLong()) },
)

fun ConfirmPostImageUploadsInput.toCommand(): ConfirmPostImageUploadsCommand = ConfirmPostImageUploadsCommand(objectKeys)

/** 만료 시각은 ISO-8601 문자열이다. */
fun PostImageUploadUrlView.toResponse(): PostImageUploadUrl = PostImageUploadUrl(
    uploadUrl = uploadUrl,
    objectKey = objectKey,
    expiresAt = expiresAt.toString(),
)

fun PostImageConfirmFailure.toResponse(): PostImageUploadFailure = PostImageUploadFailure(objectKey = objectKey, code = code)
