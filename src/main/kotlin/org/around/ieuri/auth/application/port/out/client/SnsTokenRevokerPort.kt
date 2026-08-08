package org.around.ieuri.auth.application.port.out.client

import org.around.ieuri.auth.domain.vo.Provider

/**
 * 탈퇴 시 provider 측 계정 연결을 해제하는 포트(SPEC-0012 R8~R11).
 *
 * provider마다 요구하는 값이 다르다 — Apple·Google은 저장된 refresh token이 필요하고,
 * Kakao는 어드민 키 + 회원번호(providerUserId)로 토큰 없이 해제한다.
 * 그래서 둘 다 담아 넘기고, 어느 값을 쓸지는 각 provider 구현이 고른다.
 *
 * 구현은 예외를 던지지 않는다 — 해제 실패가 탈퇴를 막아서는 안 된다(R11).
 * 실패는 반환값으로 알리고, 호출자가 감사 로그에 남긴다.
 */
interface SnsTokenRevokerPort {
    fun revoke(command: RevokeSnsConnectionCommand): SnsRevokeResult
}

/**
 * providerRefreshToken은 복호화된 평문 토큰이며 없을 수 있다(클라이언트 업데이트 전 로그인한 회원).
 * providerUserId는 provider가 준 식별자로, Kakao 어드민 키 경로가 이 값을 대상으로 쓴다.
 */
data class RevokeSnsConnectionCommand(
    val provider: Provider,
    val providerUserId: String,
    val providerRefreshToken: String?,
)

/** 해제 시도 결과. 실패 사유는 감사 로그용이며 토큰·이메일을 담지 않는다(R11). */
sealed interface SnsRevokeResult {
    /** 해제 성공. */
    data object Revoked : SnsRevokeResult

    /** 해제 대상이 아님(EMAIL 회원, 또는 토큰이 필요한데 저장된 토큰이 없는 회원). */
    data object Skipped : SnsRevokeResult

    /** 해제 실패. 탈퇴는 계속 진행된다. */
    data class Failed(val reason: String) : SnsRevokeResult
}
