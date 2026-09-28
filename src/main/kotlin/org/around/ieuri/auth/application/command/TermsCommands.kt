package org.around.ieuri.auth.application.command

/** 약관 동의 토글(SPEC-0011 R10). */
data class UpdateAgreementCommand(
    val memberId: Long,
    val termsCode: String,
    val agreed: Boolean,
)

/** 미동의 필수 약관에 동의(R12). 철회는 받지 않고, 선택 약관 코드가 섞이면 전부 거절한다. */
data class AgreeToRequiredTermsCommand(
    val memberId: Long,
    val termsCodes: List<String>,
)
