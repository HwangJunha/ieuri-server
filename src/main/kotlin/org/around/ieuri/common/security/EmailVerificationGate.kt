package org.around.ieuri.common.security

/**
 * 이메일 미인증 회원의 오퍼레이션 접근 판정(SPEC-0024 R9).
 * - 허용 목록은 화이트리스트다 — 목록에 없는 이름은 언제나 거부다.
 */
object EmailVerificationGate {

    /** 인증 화면이 쓰는 오퍼레이션(스펙 R9). `withdrawMember`·`logout`은 갇힌 회원의 탈출구다. */
    private val VERIFICATION_SCREEN_OPERATIONS: Set<String> = setOf(
        "me",
        "verifyEmail",
        "resendVerification",
        "retargetEmailVerification",
        "withdrawMember",
        "refreshToken",
        "logout",
    )

    /**
     * 인증 이전 단계의 오퍼레이션 — 토큰의 주체를 읽지 않는다.
     * 미인증 회원이 이전 토큰을 단 채 다른 계정으로 로그인하는 경로를 막지 않기 위해 허용한다.
     */
    private val PRE_AUTH_OPERATIONS: Set<String> = setOf(
        "loginEmail",
        "loginSns",
        "registerEmailMember",
        "registerSnsMember",
        "requestPasswordReset",
        "resetPassword",
        "authHealth",
    )

    /** 미인증 회원이 호출할 수 있는 오퍼레이션 전체. */
    val ALLOWED_OPERATIONS: Set<String> = VERIFICATION_SCREEN_OPERATIONS + PRE_AUTH_OPERATIONS

    /**
     * [rootFieldNames]의 오퍼레이션을 인증 상태 [emailVerified]로 호출해도 되는지.
     * - 인증 완료 회원은 전면 통과한다. 미인증 회원은 루트 필드가 전부 허용 목록에 있을 때만 통과한다.
     * - 루트 필드가 없는 요청은 통과한다.
     * - 전제: 주체가 없는(비로그인) 요청은 이 판정에 오지 않는다 — 오면 `EMAIL_NOT_VERIFIED`를 잘못 받는다.
     */
    fun isAllowed(rootFieldNames: Collection<String>, emailVerified: Boolean): Boolean = emailVerified || rootFieldNames.all { it in ALLOWED_OPERATIONS }
}
