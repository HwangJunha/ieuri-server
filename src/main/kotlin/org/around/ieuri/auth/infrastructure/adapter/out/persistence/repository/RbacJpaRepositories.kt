package org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleId
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RolePermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RolePermissionId
import org.springframework.data.jpa.repository.JpaRepository

interface RoleJpaRepository : JpaRepository<RoleEntity, Long> {
    fun findByCode(code: String): RoleEntity?
}

interface PermissionJpaRepository : JpaRepository<PermissionEntity, Long>

interface RolePermissionJpaRepository : JpaRepository<RolePermissionEntity, RolePermissionId>

interface MemberRoleJpaRepository : JpaRepository<MemberRoleEntity, MemberRoleId> {
    fun findByMemberId(memberId: Long): List<MemberRoleEntity>

    fun existsByMemberIdAndRoleId(memberId: Long, roleId: Long): Boolean

    fun deleteByMemberId(memberId: Long)
}
