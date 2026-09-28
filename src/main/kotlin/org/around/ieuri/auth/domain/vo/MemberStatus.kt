package org.around.ieuri.auth.domain.vo

/** 회원 상태. 탈퇴는 행 삭제라 상태값이 없으므로 ACTIVE / SUSPENDED 둘뿐이다. */
enum class MemberStatus {
    ACTIVE,
    SUSPENDED,
}
