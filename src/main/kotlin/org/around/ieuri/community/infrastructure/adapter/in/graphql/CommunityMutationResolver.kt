package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.BlockUserInput
import org.around.ieuri.common.graphql.generated.types.BlockUserPayload
import org.around.ieuri.common.graphql.generated.types.ConfirmPostImageUploadsInput
import org.around.ieuri.common.graphql.generated.types.ConfirmPostImageUploadsPayload
import org.around.ieuri.common.graphql.generated.types.CreateCommentInput
import org.around.ieuri.common.graphql.generated.types.CreateCommentPayload
import org.around.ieuri.common.graphql.generated.types.CreatePostImageUploadUrlsInput
import org.around.ieuri.common.graphql.generated.types.CreatePostImageUploadUrlsPayload
import org.around.ieuri.common.graphql.generated.types.CreatePostInput
import org.around.ieuri.common.graphql.generated.types.CreatePostPayload
import org.around.ieuri.common.graphql.generated.types.DeleteCommentInput
import org.around.ieuri.common.graphql.generated.types.DeleteCommentPayload
import org.around.ieuri.common.graphql.generated.types.DeletePostImageInput
import org.around.ieuri.common.graphql.generated.types.DeletePostImagePayload
import org.around.ieuri.common.graphql.generated.types.DeletePostInput
import org.around.ieuri.common.graphql.generated.types.DeletePostPayload
import org.around.ieuri.common.graphql.generated.types.EditPostInput
import org.around.ieuri.common.graphql.generated.types.EditPostPayload
import org.around.ieuri.common.graphql.generated.types.HidePostInput
import org.around.ieuri.common.graphql.generated.types.HidePostPayload
import org.around.ieuri.common.graphql.generated.types.LikePostInput
import org.around.ieuri.common.graphql.generated.types.LikePostPayload
import org.around.ieuri.common.graphql.generated.types.PostImageUploadFailure
import org.around.ieuri.common.graphql.generated.types.PostImageUploadUrl
import org.around.ieuri.common.graphql.generated.types.ReportContentInput
import org.around.ieuri.common.graphql.generated.types.ReportContentPayload
import org.around.ieuri.common.graphql.generated.types.UnblockUserInput
import org.around.ieuri.common.graphql.generated.types.UnblockUserPayload
import org.around.ieuri.common.graphql.generated.types.UnhidePostPayload
import org.around.ieuri.common.graphql.generated.types.UnlikePostInput
import org.around.ieuri.common.graphql.generated.types.UnlikePostPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.command.BlockUserCommand
import org.around.ieuri.community.application.command.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.command.CreateCommentCommand
import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeleteCommentCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.DeletePostImageCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.command.HidePostCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.command.PostImageUploadRequest
import org.around.ieuri.community.application.command.ReportContentCommand
import org.around.ieuri.community.application.port.`in`.command.BlockCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.CommentCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.PostCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.PostHideCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.PostImageUploadUseCase
import org.around.ieuri.community.application.port.`in`.command.PostLikeCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.ReportCommandUseCase
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.locale.contentLanguage
import org.springframework.stereotype.Component

/**
 * 커뮤니티 쓰기 리졸버. 전부 인증이 필요하다.
 * - 비로그인의 UnauthorizedException은 userErrors가 아니라 최상위 errors[]로 전파된다.
 */
@DgsComponent
@Component
class CommunityMutationResolver(
    private val postCommandUseCase: PostCommandUseCase,
    private val commentCommandUseCase: CommentCommandUseCase,
    private val postLikeCommandUseCase: PostLikeCommandUseCase,
    private val blockCommandUseCase: BlockCommandUseCase,
    private val postHideCommandUseCase: PostHideCommandUseCase,
    private val reportCommandUseCase: ReportCommandUseCase,
    private val postImageUploadUseCase: PostImageUploadUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /** 장소는 활성 언어(Accept-Language)에서 해소되는 것만 붙일 수 있다(SPEC-0029 R-16). */
    @DgsMutation
    fun createPost(
        @InputArgument input: CreatePostInput,
        dfe: DgsDataFetchingEnvironment,
    ): CreatePostPayload = withCommunityUserErrors(
        onError = { CreatePostPayload(success = false, userErrors = it, postId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val postId = postCommandUseCase.createPost(
            command = CreatePostCommand(
                category = TargetCategory.valueOf(input.category.name),
                catalogId = input.catalogId,
                tags = input.tags,
                title = input.title,
                body = input.body,
                photoKeys = input.photoKeys,
            ),
            memberId = memberId,
            language = dfe.contentLanguage(),
        )
        // post는 싣지 않고 postId만 준다 — 클라이언트는 `post(id:)`로 조회한다.
        CreatePostPayload(success = true, userErrors = emptyList(), postId = postId)
    }

    @DgsMutation
    fun editPost(
        @InputArgument input: EditPostInput,
        dfe: DgsDataFetchingEnvironment,
    ): EditPostPayload = withCommunityUserErrors(
        onError = { EditPostPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        postCommandUseCase.editPost(
            EditPostCommand(
                postId = input.postId,
                title = input.title,
                body = input.body,
                photoKeys = input.photoKeys,
                category = input.category?.let { TargetCategory.valueOf(it.name) },
                changeSpot = input.changeSpot ?: false,
                catalogId = input.catalogId,
                tags = input.tags,
            ),
            memberId,
            dfe.contentLanguage(),
        )
        EditPostPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun deletePost(@InputArgument input: DeletePostInput): DeletePostPayload = withCommunityUserErrors(
        onError = { DeletePostPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        postCommandUseCase.deletePost(DeletePostCommand(input.postId), memberId)
        DeletePostPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun createComment(@InputArgument input: CreateCommentInput): CreateCommentPayload = withCommunityUserErrors(
        onError = {
            CreateCommentPayload(success = false, userErrors = it, commentId = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val commentId = commentCommandUseCase.createComment(
            CreateCommentCommand(
                postId = input.postId,
                body = input.body,
                parentCommentId = input.parentCommentId,
            ),
            memberId,
        )
        // createPost와 같이 commentId만 준다.
        CreateCommentPayload(
            success = true,
            userErrors = emptyList(),
            commentId = commentId,
        )
    }

    @DgsMutation
    fun deleteComment(@InputArgument input: DeleteCommentInput): DeleteCommentPayload = withCommunityUserErrors(
        onError = { DeleteCommentPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        commentCommandUseCase.deleteComment(DeleteCommentCommand(input.commentId), memberId)
        DeleteCommentPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun likePost(@InputArgument input: LikePostInput): LikePostPayload = withCommunityUserErrors(
        onError = { LikePostPayload(success = false, userErrors = it, likeCount = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val likeCount = postLikeCommandUseCase.like(LikePostCommand(input.postId), memberId)
        LikePostPayload(success = true, userErrors = emptyList(), likeCount = likeCount)
    }

    @DgsMutation
    fun unlikePost(@InputArgument input: UnlikePostInput): UnlikePostPayload = withCommunityUserErrors(
        onError = { UnlikePostPayload(success = false, userErrors = it, likeCount = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val likeCount = postLikeCommandUseCase.unlike(LikePostCommand(input.postId), memberId)
        UnlikePostPayload(success = true, userErrors = emptyList(), likeCount = likeCount)
    }

    @DgsMutation
    fun createPostImageUploadUrls(
        @InputArgument input: CreatePostImageUploadUrlsInput,
    ): CreatePostImageUploadUrlsPayload = withCommunityUserErrors(
        onError = {
            CreatePostImageUploadUrlsPayload(success = false, userErrors = it, uploadUrls = emptyList())
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val issued = postImageUploadUseCase.issueUploadUrls(
            IssuePostImageUploadUrlsCommand(
                images = input.images.map {
                    PostImageUploadRequest(
                        contentType = it.contentType,
                        contentLength = it.contentLength.toLong(),
                    )
                },
            ),
            memberId,
        )
        CreatePostImageUploadUrlsPayload(
            success = true,
            userErrors = emptyList(),
            uploadUrls = issued.map {
                PostImageUploadUrl(
                    uploadUrl = it.uploadUrl,
                    objectKey = it.objectKey,
                    expiresAt = it.expiresAt.toString(),
                )
            },
        )
    }

    /** 장별로 성패가 갈린다. success는 전부 성공일 때만 true이고, 일부 실패여도 confirmed는 쓸 수 있다. */
    @DgsMutation
    fun confirmPostImageUploads(
        @InputArgument input: ConfirmPostImageUploadsInput,
    ): ConfirmPostImageUploadsPayload = withCommunityUserErrors(
        onError = {
            ConfirmPostImageUploadsPayload(
                success = false,
                userErrors = it,
                confirmed = emptyList(),
                failed = emptyList(),
            )
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = postImageUploadUseCase.confirmUploads(
            ConfirmPostImageUploadsCommand(input.objectKeys),
            memberId,
        )
        ConfirmPostImageUploadsPayload(
            success = result.failed.isEmpty(),
            userErrors = emptyList(),
            confirmed = result.confirmed,
            failed = result.failed.map {
                PostImageUploadFailure(objectKey = it.objectKey, code = it.code)
            },
        )
    }

    @DgsMutation
    fun deletePostImage(
        @InputArgument input: DeletePostImageInput,
    ): DeletePostImagePayload = withCommunityUserErrors(
        onError = { DeletePostImagePayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        postImageUploadUseCase.deleteImage(DeletePostImageCommand(input.objectKey), memberId)
        DeletePostImagePayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun hidePost(@InputArgument input: HidePostInput): HidePostPayload = withCommunityUserErrors(
        onError = { HidePostPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        postHideCommandUseCase.hide(HidePostCommand(input.postId), memberId)
        HidePostPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun unhidePost(@InputArgument input: HidePostInput): UnhidePostPayload = withCommunityUserErrors(
        onError = { UnhidePostPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        postHideCommandUseCase.unhide(HidePostCommand(input.postId), memberId)
        UnhidePostPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun blockUser(@InputArgument input: BlockUserInput): BlockUserPayload = withCommunityUserErrors(
        onError = { BlockUserPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        blockCommandUseCase.block(BlockUserCommand(input.targetMemberId), memberId)
        BlockUserPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun unblockUser(@InputArgument input: UnblockUserInput): UnblockUserPayload = withCommunityUserErrors(
        onError = { UnblockUserPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        blockCommandUseCase.unblock(BlockUserCommand(input.targetMemberId), memberId)
        UnblockUserPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun reportContent(@InputArgument input: ReportContentInput): ReportContentPayload = withCommunityUserErrors(
        onError = { ReportContentPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        reportCommandUseCase.report(
            ReportContentCommand(
                targetType = ReportTargetType.valueOf(input.targetType.name),
                targetId = input.targetId,
                reason = ReportReason.valueOf(input.reason.name),
                detail = input.detail,
            ),
            memberId,
        )
        ReportContentPayload(success = true, userErrors = emptyList())
    }
}
