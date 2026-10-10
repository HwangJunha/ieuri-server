package org.around.ieuri.auth.domain.member.event

import org.around.ieuri.auth.domain.member.Provider

/** 회원이 인증 대기 주소를 정정했다. 주소는 담지 않는다(POL-013). */
data class EmailVerificationRetargeted(
    val memberId: Long,
    val provider: Provider,
)
