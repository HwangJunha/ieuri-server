package org.around.ieuri.auth.application.member.provided.dto

/** 약관별 동의/비동의 입력. 비동의도 이력으로 남긴다. */
data class AgreementInput(
    val termsCode: String,
    val agreed: Boolean,
) {
    init {
        require(termsCode.isNotBlank()) { "termsCode는 비어 있을 수 없습니다." }
    }
}
