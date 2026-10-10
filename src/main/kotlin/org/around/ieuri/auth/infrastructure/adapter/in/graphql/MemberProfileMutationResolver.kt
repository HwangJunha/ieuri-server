package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.member.provided.MemberProfileUseCase
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.RemoveProfileImageCommand
import org.around.ieuri.common.graphql.generated.types.ChangeNicknameInput
import org.around.ieuri.common.graphql.generated.types.ChangeNicknamePayload
import org.around.ieuri.common.graphql.generated.types.ConfirmProfileImageUploadInput
import org.around.ieuri.common.graphql.generated.types.ConfirmProfileImageUploadPayload
import org.around.ieuri.common.graphql.generated.types.CreateProfileImageUploadUrlInput
import org.around.ieuri.common.graphql.generated.types.CreateProfileImageUploadUrlPayload
import org.around.ieuri.common.graphql.generated.types.RemoveProfileImagePayload
import org.around.ieuri.common.security.CurrentMemberProvider

/** 프로필 수정 뮤테이션 리졸버(SPEC-0011 R5·R6). 본인 대상이며 이미지 바이트는 서버를 거치지 않는다. */
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
