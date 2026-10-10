package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import org.around.ieuri.auth.application.member.provided.RoleQueryUseCase
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import org.around.ieuri.common.graphql.generated.types.RoleInfo as GqlRoleInfo

/** 역할 조회 쿼리 리졸버. assignableRoles는 role:assign 권한을 요구한다. */
@DgsComponent
class RoleQueryResolver(
    private val roleQueryUseCase: RoleQueryUseCase,
) {
    @DgsQuery
    @RequiresPermission(Permission.ROLE_ASSIGN)
    fun assignableRoles(): List<GqlRoleInfo> = roleQueryUseCase.assignableRoles().map {
        GqlRoleInfo(code = it.code, name = it.name, description = it.description)
    }
}
