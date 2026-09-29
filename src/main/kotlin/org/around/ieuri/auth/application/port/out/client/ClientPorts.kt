package org.around.ieuri.auth.application.port.out.client

import org.around.ieuri.auth.domain.vo.Provider

/** SNS id_token을 검증하고 신원을 반환하는 포트. 서명·iss·aud 검증은 provider별 구현이 한다. */
interface SnsIdentityVerifierPort {
    fun verify(provider: Provider, idToken: String): SnsIdentity
}

/** 검증된 SNS 신원. 애플은 최초 인증에서만 이름/이메일을 준다. email이 null이면 사용자가 직접 입력해야 한다. */
data class SnsIdentity(
    val providerUserId: String,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)

/**
 * authorization code를 폐기용 refresh token으로 교환하는 포트(SPEC-0012 R5).
 * - 교환 실패는 예외가 아니라 null이다 — 로그인·가입을 막지 않는다(R7).
 * - Kakao는 어드민 키로 해제하므로 교환하지 않고 null을 반환한다.
 */
interface SnsTokenExchangerPort {
    fun exchangeForRefreshToken(provider: Provider, authorizationCode: String): String?
}

/** 이메일 발송 포트. 발송 실패는 예외로 알리지 않는다(이미 커밋된 트랜잭션의 뒷정리 경로다). */
interface EmailSenderPort {
    fun sendVerificationCode(toEmail: String, code: String)

    fun sendPasswordResetCode(toEmail: String, code: String)
}
