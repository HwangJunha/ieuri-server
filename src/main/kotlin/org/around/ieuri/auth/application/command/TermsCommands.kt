package org.around.ieuri.auth.application.command

/**
 * 선택 약관 동의 토글(SPEC-0011 R10). 필수 약관 코드는 거절된다 —
 * 필수 약관 철회는 사실상 서비스 이용 중단이라 탈퇴로만 가능하다.
 */
data class UpdateAgreementCommand(
    val memberId: Long,
    val termsCode: String,
    val agreed: Boolean,
)

/**
 * 미동의 필수 약관에 동의(R12). 동의만 받으며 철회는 받지 않는다.
 * 선택 약관 코드가 섞이면 전부 거절한다.
 */
data class AgreeToRequiredTermsCommand(
    val memberId: Long,
    val termsCodes: List<String>,
)
