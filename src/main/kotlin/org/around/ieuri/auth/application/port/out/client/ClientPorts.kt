package org.around.ieuri.auth.application.port.out.client

import org.around.ieuri.auth.domain.vo.Provider

/**
 * SNS id_token/사용자정보를 검증하고 신원을 반환하는 포트.
 * 실제 서명·발급자(iss)·audience 검증은 구현(어댑터)에서 provider별로 처리한다.
 */
interface SnsIdentityVerifierPort {
    fun verify(provider: Provider, idToken: String): SnsIdentity
}

/**
 * 검증된 SNS 신원. 애플은 최초 인증에서만 이름/이메일이 오므로 그 값을 그대로 담는다.
 * email이 null이면(카카오 무이메일/애플 미제공) 사용자가 직접 입력해야 한다.
 */
data class SnsIdentity(
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
    val nickname: String?,
    val profileImageUrl: String?,
)

/** 이메일 발송 포트. 현재 Gmail SMTP 구현, 추후 Resend로 교체. */
interface EmailSenderPort {
    fun sendVerificationCode(toEmail: String, code: String)
}
