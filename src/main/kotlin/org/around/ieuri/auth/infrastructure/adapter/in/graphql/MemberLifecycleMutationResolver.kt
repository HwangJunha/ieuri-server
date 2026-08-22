package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.port.`in`.command.MemberLifecycleUseCase
import org.around.ieuri.common.graphql.generated.types.AdminActionPayload
import org.around.ieuri.common.graphql.generated.types.ReactivateMemberInput
import org.around.ieuri.common.graphql.generated.types.SuspendMemberInput
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.common.graphql.generated.types.WithdrawMemberPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import java.time.Instant

/**
 * 회원 상태(탈퇴/정지/복구) GraphQL 뮤테이션 리졸버.
 * 탈퇴는 accessToken 인증 주체(본인)로 처리하고, 정지/복구는 권한(member:suspend/member:reactivate)으로 보호한다.
 */
@DgsComponent
class MemberLifecycleMutationResolver(
    private val memberLifecycleUseCase: MemberLifecycleUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun withdrawMember(): WithdrawMemberPayload = withUserErrors(
        onError = { WithdrawMemberPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = memberLifecycleUseCase.withdraw(WithdrawMemberCommand(memberId))
        WithdrawMemberPayload(success = result.success, userErrors = emptyList())
    }

    @DgsMutation
    @RequiresPermission(Permission.MEMBER_SUSPEND)
    fun suspendMember(@InputArgument input: SuspendMemberInput): AdminActionPayload = withUserErrors(
        onError = { AdminActionPayload(success = false, userErrors = it) },
    ) {
        val result = memberLifecycleUseCase.suspend(
            SuspendMemberCommand(
                memberId = input.memberId,
                reason = input.reason,
                suspendedUntil = input.suspendedUntil?.let { Instant.parse(it) },
            ),
        )
        AdminActionPayload(success = result.success, userErrors = emptyList())
    }

    @DgsMutation
    @RequiresPermission(Permission.MEMBER_REACTIVATE)
    fun reactivateMember(@InputArgument input: ReactivateMemberInput): AdminActionPayload = withUserErrors(
        onError = { AdminActionPayload(success = false, userErrors = it) },
    ) {
        val result = memberLifecycleUseCase.reactivate(ReactivateMemberCommand(input.memberId))
        AdminActionPayload(success = result.success, userErrors = emptyList())
    }
}
