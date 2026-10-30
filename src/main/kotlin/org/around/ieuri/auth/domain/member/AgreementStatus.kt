package org.around.ieuri.auth.domain.member

import java.time.Instant

/** 시행 중인 약관 한 건에 대한 회원의 동의 상태. 미동의면 [agreedAt]은 null이다. */
data class AgreementStatus(
    val terms: Terms,
    val agreed: Boolean,
    val agreedAt: Instant?,
)
