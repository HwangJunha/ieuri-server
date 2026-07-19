package org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RolePermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RolePermissionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RoleJpaRepository : JpaRepository<RoleEntity, Long> {
    fun findByCode(code: String): RoleEntity?
}

interface PermissionJpaRepository : JpaRepository<PermissionEntity, Long>

interface RolePermissionJpaRepository : JpaRepository<RolePermissionEntity, RolePermissionId> {
    /**
     * 주어진 역할들의 권한 코드를 한 번에 조회(역할별 N+1 회피). role_permission↔permission 애드혹 조인.
     */
    @Query(
        """
        SELECT rp.roleId AS roleId, p.code AS code
        FROM RolePermissionEntity rp
        JOIN PermissionEntity p ON p.id = rp.permissionId
        WHERE rp.roleId IN :roleIds
        """,
    )
    fun findPermissionCodesByRoleIds(@Param("roleIds") roleIds: Collection<Long>): List<RolePermissionCodeRow>
}

/** (roleId, permissionCode) 프로젝션 행. */
interface RolePermissionCodeRow {
    val roleId: Long
    val code: String
}

interface MemberRoleJpaRepository : JpaRepository<MemberRoleEntity, MemberRoleId> {
    fun findByMemberId(memberId: Long): List<MemberRoleEntity>

    fun existsByMemberIdAndRoleId(memberId: Long, roleId: Long): Boolean

    fun deleteByMemberId(memberId: Long)
}
