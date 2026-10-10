package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.member.Role
/** 역할/권한 쓰기 포트. 회원의 역할 매핑을 갱신하고 역할 애그리거트를 로드한다. 화면용 조회는 port/out/query가 맡는다. */
interface RoleRepositoryPort {
    /** 코드로 역할(+권한)을 로드. 없으면 null. */
    fun findByCode(code: String): Role?

    /** 회원에게 부여된 모든 역할(+각 역할의 권한)을 로드. 유효 권한 = 이들의 권한 합집합. */
    fun findRolesOfMember(memberId: Long): List<Role>

    /** 회원에게 역할을 부여(멱등). 이미 있으면 false, 새로 부여하면 true. */
    fun assignRoleToMember(memberId: Long, roleCode: String): Boolean

    /** 회원의 역할을 회수(멱등). 실제로 회수했으면 true, 원래 없었으면 false. */
    fun revokeRoleFromMember(memberId: Long, roleCode: String): Boolean
}
