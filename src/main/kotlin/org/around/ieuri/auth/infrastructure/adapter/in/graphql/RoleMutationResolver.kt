package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.AssignRoleCommand
import org.around.ieuri.auth.application.command.RevokeRoleCommand
import org.around.ieuri.auth.application.port.`in`.command.RoleUseCase
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import org.around.ieuri.common.graphql.generated.types.AssignRoleInput
import org.around.ieuri.common.graphql.generated.types.RevokeRoleInput
import org.around.ieuri.common.graphql.generated.types.RoleChangePayload
import org.around.ieuri.common.security.CurrentMemberProvider

/** 역할 부여/회수 뮤테이션 리졸버. role:assign/role:revoke 권한을 요구하고 요청 주체(actor)를 넘긴다. */
@DgsComponent
class RoleMutationResolver(
    private val roleUseCase: RoleUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    @RequiresPermission(Permission.ROLE_ASSIGN)
    fun assignRole(@InputArgument input: AssignRoleInput): RoleChangePayload = withUserErrors(
        onError = { RoleChangePayload(success = false, userErrors = it, changed = null) },
    ) {
        val actorId = currentMemberProvider.requireMemberId()
        val result = roleUseCase.assign(
            AssignRoleCommand(
                actorMemberId = actorId,
                targetMemberId = input.memberId,
                roleCode = input.roleCode,
            ),
        )
        RoleChangePayload(success = result.success, userErrors = emptyList(), changed = result.changed)
    }

    @DgsMutation
    @RequiresPermission(Permission.ROLE_REVOKE)
    fun revokeRole(@InputArgument input: RevokeRoleInput): RoleChangePayload = withUserErrors(
        onError = { RoleChangePayload(success = false, userErrors = it, changed = null) },
    ) {
        val actorId = currentMemberProvider.requireMemberId()
        val result = roleUseCase.revoke(
            RevokeRoleCommand(
                actorMemberId = actorId,
                targetMemberId = input.memberId,
                roleCode = input.roleCode,
            ),
        )
        RoleChangePayload(success = result.success, userErrors = emptyList(), changed = result.changed)
    }
}
