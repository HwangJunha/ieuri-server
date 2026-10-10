package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.graphql.support.withUserErrors
import org.around.ieuri.auth.adapter.security.RequiresPermission
import org.around.ieuri.auth.application.member.provided.MemberLifecycleUseCase
import org.around.ieuri.auth.application.member.provided.MemberWithdrawalUseCase
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawMemberCommand
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.common.graphql.clientIp
import org.around.ieuri.common.graphql.generated.types.AdminActionPayload
import org.around.ieuri.common.graphql.generated.types.ReactivateMemberInput
import org.around.ieuri.common.graphql.generated.types.SuspendMemberInput
import org.around.ieuri.common.graphql.generated.types.WithdrawMemberPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import java.time.Instant

/** 회원 상태 뮤테이션 리졸버. 탈퇴는 본인 인증, 정지/복구는 member:suspend/member:reactivate 권한으로 보호한다. */
@DgsComponent
class MemberLifecycleMutationResolver(
    private val memberLifecycleUseCase: MemberLifecycleUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val memberWithdrawalUseCase: MemberWithdrawalUseCase,
) {
    @DgsMutation
    fun withdrawMember(dfe: DataFetchingEnvironment): WithdrawMemberPayload = withUserErrors(
        onError = { WithdrawMemberPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = memberWithdrawalUseCase.withdraw(WithdrawMemberCommand(memberId, dfe.clientIp()))
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
