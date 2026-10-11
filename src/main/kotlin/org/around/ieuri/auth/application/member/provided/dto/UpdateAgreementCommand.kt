package org.around.ieuri.auth.application.member.provided.dto
/** 약관 동의 토글(SPEC-0011 R10). */
data class UpdateAgreementCommand(
    val termsCode: String,
    val agreed: Boolean,
)
