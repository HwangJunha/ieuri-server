package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QPermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QRolePermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberRoleJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.RoleJpaRepository
import org.springframework.stereotype.Component
import java.time.Clock

/** 역할/권한 쓰기 포트 구현. 회원의 역할들과 각 역할의 권한 코드를 조합해 도메인 Role로 재구성한다. */
@Component
class RolePersistenceAdapter(
    private val roleRepository: RoleJpaRepository,
    private val memberRoleRepository: MemberRoleJpaRepository,
    private val queryFactory: JPAQueryFactory,
    private val clock: Clock,
) : RoleRepositoryPort {

    private val rolePermission = QRolePermissionEntity.rolePermissionEntity
    private val permission = QPermissionEntity.permissionEntity

    override fun findByCode(code: String): Role? {
        val entity = roleRepository.findByCode(code) ?: return null
        val permissionCodes = permissionCodesByRoleIds(listOf(requireNotNull(entity.id)))
        return entity.toDomain(permissionCodes[entity.id].orEmpty())
    }

    override fun findRolesOfMember(memberId: Long): List<Role> {
        val roleIds = memberRoleRepository.findByMemberId(memberId).map { it.roleId }
        if (roleIds.isEmpty()) return emptyList()

        val permissionCodes = permissionCodesByRoleIds(roleIds)
        return roleRepository.findAllById(roleIds)
            .map { entity -> entity.toDomain(permissionCodes[entity.id].orEmpty()) }
    }

    /** 역할별 권한 코드를 한 번에 조회한다(역할별 N+1 회피). */
    private fun permissionCodesByRoleIds(roleIds: Collection<Long>): Map<Long, List<String>> = queryFactory
        .select(rolePermission.roleId, permission.code)
        .from(rolePermission)
        .join(permission).on(permission.id.eq(rolePermission.permissionId))
        .where(rolePermission.roleId.`in`(roleIds))
        .fetch()
        .groupBy(
            { requireNotNull(it.get(rolePermission.roleId)) },
            { requireNotNull(it.get(permission.code)) },
        )

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
