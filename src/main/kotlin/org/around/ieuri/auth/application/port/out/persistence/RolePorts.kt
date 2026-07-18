package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.aggregate.Role

/**
 * 역할/권한 쓰기·조회 포트(CQRS 쓰기 측). 회원의 역할 매핑을 갱신하고, 역할 애그리거트를 도메인 용어로 로드한다.
 * 토큰 발급 시 유효 권한 flatten, 역할 부여/회수, 자기 강등 방지 판정에 쓰인다. JPA를 노출하지 않는다.
 *
 * (읽기 화면용 회원 조회는 CQRS 규약에 따라 별도 `port/out/query`로 분리한다 — 이 포트로 읽지 않는다.)
 */
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
