package org.around.ieuri.auth.domain.vo

/**
 * 회원 상태. 탈퇴는 행 삭제(hard delete)로 처리하므로 상태값으로 남기지 않는다.
 * 따라서 상태는 ACTIVE / SUSPENDED 둘뿐이다.
 */
enum class MemberStatus {
    ACTIVE,
    SUSPENDED,
}
