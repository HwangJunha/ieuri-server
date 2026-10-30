package org.around.ieuri.auth.application.member.provided.dto

import org.around.ieuri.auth.domain.member.Provider

/** 탈퇴 전에 provider 연결을 끊는 데 필요한 값. 토큰은 복호화된 평문이며 없을 수 있다. */
data class WithdrawalTargetView(
    val provider: Provider,
    val providerUserId: String,
    val providerRefreshToken: String?,
)
