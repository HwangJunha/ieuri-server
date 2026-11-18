package org.around.ieuri.auth.domain.member.event

import org.around.ieuri.auth.domain.member.Provider

/** 회원의 이메일이 바뀌었다. 주소는 담지 않는다(POL-013). */
data class EmailChanged(
    val memberId: Long,
    val provider: Provider,
    val loginIdentifierUpdated: Boolean,
)
