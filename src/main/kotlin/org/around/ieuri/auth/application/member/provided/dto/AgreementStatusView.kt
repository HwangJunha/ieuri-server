package org.around.ieuri.auth.application.member.provided.dto

import org.around.ieuri.auth.domain.member.AgreementStatus
import java.time.Instant

/** 약관 1건의 동의 상태(SPEC-0011 R9). 새 버전이 시행되면 이전 버전 동의는 이월되지 않아 agreed가 거짓이 된다. */
data class AgreementStatusView(
    val termsCode: String,
    val required: Boolean,
    val agreed: Boolean,
    /** 시행 중인 버전에 동의한 시각. 미동의면 null. */
    val agreedAt: Instant?,
    val version: String,
) {
    companion object {
        fun of(status: AgreementStatus): AgreementStatusView = AgreementStatusView(
            termsCode = status.terms.code,
            required = status.terms.required,
            agreed = status.agreed,
            agreedAt = status.agreedAt,
            version = status.terms.version,
        )
    }
}
