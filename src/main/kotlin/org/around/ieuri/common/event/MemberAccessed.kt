package org.around.ieuri.common.event

/**
 * 개인정보처리시스템에 접속했다(「개인정보의 안전성 확보조치 기준」 제8조).
 *
 * **모든 요청이 아니라 "개인정보 접속"만 발행한다.** 기준이 요구하는 것은 개인정보처리시스템에
 * 대한 접속기록이지 조회 API 호출 전체가 아니다 — 스팟 목록을 넘길 때마다 행을 남기면 읽기가
 * 전부 쓰기가 되고, 정작 침해사고 조사에서 봐야 할 인증·계정 변경이 그 안에 묻힌다.
 *
 * 발행 지점은 [AccessType]이 정의하는 것으로 한정한다.
 *
 * `LocationAccessed`와 같은 자리에 두는 이유도 같다 — 발행자가 여럿(auth의 여러 서비스)이고
 * auth의 리스너가 받는다.
 *
 * @param memberId 접속한 회원. 남길 "누구"가 없으면 발행하지 않는다.
 * @param accessType 수행업무.
 * @param ipAddress 접속지 정보. 판정 불가 시 null — **모르는 것을 채워 넣지 않는다.**
 */
data class MemberAccessed(
    val memberId: Long,
    val accessType: AccessType,
    val ipAddress: String?,
)

/**
 * 접속기록을 남기는 수행업무의 종류.
 *
 * **열거형인 것이 요지다** — 문자열이면 발행 지점마다 표기가 갈리고, 무엇이 기록 대상인지가
 * 코드 전체에 흩어진다. 대상을 늘리려면 여기에 상수를 더해야 하므로 목록이 한곳에 남는다.
 */
enum class AccessType {
    /** 로그인 성공. **실패한 시도는 남기지 않는다** — 남길 "누구"가 특정되지 않는다. */
    LOGIN,

    /** 로그아웃. */
    LOGOUT,

    /** 이메일 주소 변경 요청. */
    EMAIL_CHANGE,

    /** 회원 탈퇴(계정·개인정보 삭제). */
    WITHDRAW,
}
