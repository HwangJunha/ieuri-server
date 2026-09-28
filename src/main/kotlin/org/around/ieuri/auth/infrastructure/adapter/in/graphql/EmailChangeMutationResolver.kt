package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.application.command.ChangeEmailCommand
import org.around.ieuri.auth.application.command.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.command.ResendEmailChangeCommand
import org.around.ieuri.auth.application.port.`in`.command.EmailChangeUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.around.ieuri.common.graphql.clientIp
import org.around.ieuri.common.graphql.generated.types.ChangeEmailInput
import org.around.ieuri.common.graphql.generated.types.ChangeEmailPayload
import org.around.ieuri.common.graphql.generated.types.ConfirmEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ConfirmEmailChangePayload
import org.around.ieuri.common.graphql.generated.types.ResendEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ResendEmailChangePayload
import org.around.ieuri.common.security.CurrentMemberProvider
import java.util.UUID

/**
 * 이메일 변경 뮤테이션 리졸버(SPEC-0011 R7·R8). 전부 인증 주체(본인) 대상이라 권한 검사가 없다.
 *
 * **코드 검증 실패는 `userErrors`가 아니라 `failureReason`으로 내려간다** — 요청은 정상
 * 처리됐고 코드가 틀렸을 뿐이라 오류가 아니다. 기존 `verifyEmail`과 같은 규약이다.
 */
@DgsComponent
class EmailChangeMutationResolver(
    private val emailChangeUseCase: EmailChangeUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val eventPublisher: DomainEventPublisherPort,
) {
    @DgsMutation
    fun changeEmail(
        @InputArgument input: ChangeEmailInput,
        dfe: DataFetchingEnvironment,
    ): ChangeEmailPayload = withUserErrors(
        onError = { ChangeEmailPayload(success = false, userErrors = it, verificationId = null, expiresAt = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.changeEmail(ChangeEmailCommand(memberId, input.newEmail))
        // 접속기록(기준 제8조). 개인정보(이메일) 변경이라 남겨야 하는 수행업무다.
        eventPublisher.publish(MemberAccessed(memberId, AccessType.EMAIL_CHANGE, dfe.clientIp()))
        ChangeEmailPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }

    @DgsMutation
    fun confirmEmailChange(
        @InputArgument input: ConfirmEmailChangeInput,
    ): ConfirmEmailChangePayload = withUserErrors(
        onError = {
            ConfirmEmailChangePayload(success = false, userErrors = it, email = null, failureReason = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.confirmEmailChange(
            ConfirmEmailChangeCommand(memberId, UUID.fromString(input.verificationId), input.code),
        )
        ConfirmEmailChangePayload(
            // 코드가 틀린 것은 오류가 아니라 결과다 — success는 요청 처리 여부를 뜻한다.
            success = true,
            userErrors = emptyList(),
            email = result.email,
            failureReason = result.failureReason,
        )
    }

    @DgsMutation
    fun resendEmailChange(
        @InputArgument input: ResendEmailChangeInput,
    ): ResendEmailChangePayload = withUserErrors(
        onError = {
            ResendEmailChangePayload(success = false, userErrors = it, verificationId = null, expiresAt = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.resendEmailChange(
            ResendEmailChangeCommand(memberId, UUID.fromString(input.verificationId)),
        )
        ResendEmailChangePayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }
}
