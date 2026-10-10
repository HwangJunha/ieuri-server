package org.around.ieuri.auth.domain.member.event

import org.around.ieuri.auth.domain.member.Terms

/** 회원이 약관에 동의하거나 철회했다. 약관은 식별자만 담는다(POL-013). */
data class TermsAgreementChanged(
    val memberId: Long,
    val terms: List<Terms>,
    val agreed: Boolean,
)
