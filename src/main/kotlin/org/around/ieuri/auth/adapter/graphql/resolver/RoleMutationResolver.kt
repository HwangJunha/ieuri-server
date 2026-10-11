package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.adapter.graphql.mapper.toCommand
import org.around.ieuri.auth.adapter.graphql.support.withAuthUserErrors
import org.around.ieuri.auth.adapter.security.authorization.RequiresPermission
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.common.graphql.generated.types.AssignRoleInput
import org.around.ieuri.common.graphql.generated.types.RevokeRoleInput
import org.around.ieuri.common.graphql.generated.types.RoleChangePayload
import org.around.ieuri.common.security.CurrentMemberProvider

/** 역할 부여/회수 뮤테이션 리졸버. role:assign/role:revoke 권한을 요구하고 요청 주체(actor)를 넘긴다. */
@DgsComponent
class RoleMutationResolver(
    private val memberCommandUseCase: MemberCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    @RequiresPermission(Permission.ROLE_ASSIGN)
    fun assignRole(@InputArgument input: AssignRoleInput): RoleChangePayload = withAuthUserErrors(
        onError = { RoleChangePayload(success = false, userErrors = it, changed = null) },
    ) {
        val actorId = currentMemberProvider.requireMemberId()
        val result = memberCommandUseCase.assignRole(input.toCommand(), actorId)
        RoleChangePayload(success = result.success, userErrors = emptyList(), changed = result.changed)
    }

    @DgsMutation
    @RequiresPermission(Permission.ROLE_REVOKE)
    fun revokeRole(@InputArgument input: RevokeRoleInput): RoleChangePayload = withAuthUserErrors(
        onError = { RoleChangePayload(success = false, userErrors = it, changed = null) },
    ) {
        val actorId = currentMemberProvider.requireMemberId()
        val result = memberCommandUseCase.revokeRole(input.toCommand(), actorId)
        RoleChangePayload(success = result.success, userErrors = emptyList(), changed = result.changed)
    }
}
