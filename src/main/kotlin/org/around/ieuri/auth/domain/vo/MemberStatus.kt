package org.around.ieuri.auth.domain.vo

/**
 * 회원 상태. 이번 스텝에서는 ACTIVE만 사용하고,
 * 탈퇴/정지(다음 스텝)를 위한 상태값을 미리 확보해 둔다.
 */
enum class MemberStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN,
}
