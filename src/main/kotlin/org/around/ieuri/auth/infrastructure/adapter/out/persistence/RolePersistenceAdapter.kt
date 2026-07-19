package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberRoleJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.RoleJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.RolePermissionJpaRepository
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * 역할/권한 쓰기 포트 구현. JPA 엔티티↔도메인(Role) 매핑을 담당한다.
 * 유효 권한은 역할의 권한 합집합이므로, 회원의 역할들과 각 역할의 권한 코드를 조합해 도메인 Role로 재구성한다.
 */
@Component
class RolePersistenceAdapter(
    private val roleRepository: RoleJpaRepository,
    private val rolePermissionRepository: RolePermissionJpaRepository,
    private val memberRoleRepository: MemberRoleJpaRepository,
    private val clock: Clock,
) : RoleRepositoryPort {

    override fun findByCode(code: String): Role? {
        val entity = roleRepository.findByCode(code) ?: return null
        val id = requireNotNull(entity.id)
        val permissions = rolePermissionRepository.findPermissionCodesByRoleIds(listOf(id))
            .map { Permission(it.code) }
            .toSet()
        return Role(code = entity.code, name = entity.name, permissions = permissions)
    }

    override fun findRolesOfMember(memberId: Long): List<Role> {
        val roleIds = memberRoleRepository.findByMemberId(memberId).map { it.roleId }
        if (roleIds.isEmpty()) return emptyList()

        val permissionsByRole = rolePermissionRepository.findPermissionCodesByRoleIds(roleIds)
            .groupBy({ it.roleId }, { Permission(it.code) })

        return roleRepository.findAllById(roleIds).map { entity ->
            Role(
                code = entity.code,
                name = entity.name,
                permissions = (permissionsByRole[entity.id] ?: emptyList()).toSet(),
            )
        }
    }

    override fun assignRoleToMember(memberId: Long, roleCode: String): Boolean {
        val roleId = roleRepository.findByCode(roleCode)?.id ?: return false
        if (memberRoleRepository.existsByMemberIdAndRoleId(memberId, roleId)) return false
        memberRoleRepository.save(
            MemberRoleEntity(memberId = memberId, roleId = roleId, assignedAt = clock.instant()),
        )
        return true
    }

    override fun revokeRoleFromMember(memberId: Long, roleCode: String): Boolean {
        val roleId = roleRepository.findByCode(roleCode)?.id ?: return false
        if (!memberRoleRepository.existsByMemberIdAndRoleId(memberId, roleId)) return false
        memberRoleRepository.deleteById(MemberRoleId(memberId = memberId, roleId = roleId))
        return true
    }
}
