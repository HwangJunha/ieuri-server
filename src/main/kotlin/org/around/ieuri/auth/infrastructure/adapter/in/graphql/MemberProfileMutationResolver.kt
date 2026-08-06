package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.ChangeNicknameCommand
import org.around.ieuri.auth.application.command.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.command.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.command.RemoveProfileImageCommand
import org.around.ieuri.auth.application.port.`in`.command.MemberProfileUseCase
import org.around.ieuri.common.graphql.generated.types.ChangeNicknameInput
import org.around.ieuri.common.graphql.generated.types.ChangeNicknamePayload
import org.around.ieuri.common.graphql.generated.types.ConfirmProfileImageUploadInput
import org.around.ieuri.common.graphql.generated.types.ConfirmProfileImageUploadPayload
import org.around.ieuri.common.graphql.generated.types.CreateProfileImageUploadUrlInput
import org.around.ieuri.common.graphql.generated.types.CreateProfileImageUploadUrlPayload
import org.around.ieuri.common.graphql.generated.types.RemoveProfileImagePayload
import org.around.ieuri.common.security.CurrentMemberProvider

/**
 * 프로필 수정 뮤테이션 리졸버(SPEC-0011 R5·R6). 전부 인증 주체(본인) 대상이라 권한 검사가 없다.
 *
 * 이미지 업로드는 3단계이며 **서버는 바이트를 중계하지 않는다** — 발급받은 presigned URL로
 * 클라이언트가 스토리지에 직접 PUT한 뒤 확인만 서버에 알린다.
 */
@DgsComponent
class MemberProfileMutationResolver(
    private val memberProfileUseCase: MemberProfileUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun changeNickname(@InputArgument input: ChangeNicknameInput): ChangeNicknamePayload = withUserErrors(
        onError = { ChangeNicknamePayload(success = false, userErrors = it, nickname = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val nickname = memberProfileUseCase.changeNickname(
            ChangeNicknameCommand(memberId = memberId, nickname = input.nickname),
        )
        ChangeNicknamePayload(success = true, userErrors = emptyList(), nickname = nickname)
    }

    @DgsMutation
    fun createProfileImageUploadUrl(
        @InputArgument input: CreateProfileImageUploadUrlInput,
    ): CreateProfileImageUploadUrlPayload = withUserErrors(
        onError = {
            CreateProfileImageUploadUrlPayload(
                success = false,
                userErrors = it,
                uploadUrl = null,
                objectKey = null,
                expiresAt = null,
            )
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val view = memberProfileUseCase.issueProfileImageUploadUrl(
            IssueProfileImageUploadUrlCommand(
                memberId = memberId,
                contentType = input.contentType,
                contentLength = input.contentLength.toLong(),
            ),
        )
        CreateProfileImageUploadUrlPayload(
            success = true,
            userErrors = emptyList(),
            uploadUrl = view.uploadUrl,
            objectKey = view.objectKey,
            expiresAt = view.expiresAt.toString(),
        )
    }

    @DgsMutation
    fun confirmProfileImageUpload(
        @InputArgument input: ConfirmProfileImageUploadInput,
    ): ConfirmProfileImageUploadPayload = withUserErrors(
        onError = {
            ConfirmProfileImageUploadPayload(success = false, userErrors = it, profileImageKey = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val key = memberProfileUseCase.confirmProfileImageUpload(
            ConfirmProfileImageUploadCommand(memberId = memberId, objectKey = input.objectKey),
        )
        ConfirmProfileImageUploadPayload(success = true, userErrors = emptyList(), profileImageKey = key)
    }

    @DgsMutation
    fun removeProfileImage(): RemoveProfileImagePayload = withUserErrors(
        onError = { RemoveProfileImagePayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        memberProfileUseCase.removeProfileImage(RemoveProfileImageCommand(memberId))
        RemoveProfileImagePayload(success = true, userErrors = emptyList())
    }
}
