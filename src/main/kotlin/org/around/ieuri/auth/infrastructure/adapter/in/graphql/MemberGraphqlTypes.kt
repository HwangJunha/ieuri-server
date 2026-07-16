package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

/**
 * GraphQL 요청 입력 DTO와 응답 페이로드. 인바운드 어댑터 소유이며,
 * 리졸버가 이 타입을 커맨드/애플리케이션 결과와 서로 매핑한다(application은 DGS 타입을 알지 못한다).
 */
data class RegisterMemberInputDto(
    val provider: String,
    val idToken: String,
    val email: String,
    val ageVerified: Boolean,
    val agreements: List<AgreementInputDto> = emptyList(),
)

data class AgreementInputDto(
    val termsCode: String,
    val agreed: Boolean,
)

data class VerifyEmailInputDto(
    val verificationId: String,
    val code: String,
)

data class ResendVerificationInputDto(
    val verificationId: String,
)

data class RegisterMemberPayload(
    val emailVerified: Boolean,
    val verificationId: String?,
)

data class VerifyEmailPayload(
    val verified: Boolean,
    val failureReason: String?,
)

data class ResendVerificationPayload(
    val verificationId: String,
    val expiresAt: String,
)
