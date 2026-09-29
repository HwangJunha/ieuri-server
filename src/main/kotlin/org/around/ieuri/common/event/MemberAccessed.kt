package org.around.ieuri.common.event

/**
 * 개인정보처리시스템에 접속했다(「개인정보의 안전성 확보조치 기준」 제8조).
 * - 모든 요청이 아니라 [AccessType]이 정의하는 접속만 발행한다.
 * - [memberId]가 없으면 발행하지 않는다. [ipAddress]는 판정 불가 시 null이다.
 */
data class MemberAccessed(
    val memberId: Long,
    val accessType: AccessType,
    val ipAddress: String?,
)

/** 접속기록을 남기는 수행업무의 종류. 대상을 늘리려면 여기에 상수를 더한다. */
enum class AccessType {
    /** 로그인 성공. 실패한 시도는 남기지 않는다. */
    LOGIN,

    /** 로그아웃. */
    LOGOUT,

    /** 이메일 주소 변경 요청. */
    EMAIL_CHANGE,

    /** 회원 탈퇴(계정·개인정보 삭제). */
    WITHDRAW,
}
