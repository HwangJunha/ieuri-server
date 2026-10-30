package org.around.ieuri.auth.application.member.required.dto
import org.around.ieuri.auth.domain.member.Provider

/** providerRefreshToken은 복호화된 평문이며 없을 수 있다(클라이언트 업데이트 전 로그인한 회원). */
data class RevokeSnsConnectionCommand(
    val provider: Provider,
    val providerUserId: String,
    val providerRefreshToken: String?,
)
