package org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RoleEntity

/** 역할 엔티티와 그 역할의 권한 코드를 도메인 [Role]로 옮긴다. */
fun RoleEntity.toDomain(permissionCodes: Collection<String>): Role =
    Role(
        code = code,
        name = name,
        permissions = permissionCodes.map { Permission(it) }.toSet(),
    )
