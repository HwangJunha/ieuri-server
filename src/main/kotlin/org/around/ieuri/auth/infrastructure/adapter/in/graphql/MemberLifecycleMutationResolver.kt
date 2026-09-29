package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.port.`in`.command.MemberLifecycleUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
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
    private val eventPublisher: DomainEventPublisherPort,
) {
    @DgsMutation
    fun withdrawMember(dfe: DataFetchingEnvironment): WithdrawMemberPayload = withUserErrors(
        onError = { WithdrawMemberPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = memberLifecycleUseCase.withdraw(WithdrawMemberCommand(memberId))
        // 접속기록(기준 제8조). 계정·개인정보 삭제라 남겨야 하는 수행업무다.
        eventPublisher.publish(MemberAccessed(memberId, AccessType.WITHDRAW, dfe.clientIp()))
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
