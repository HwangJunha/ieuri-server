package org.around.ieuri.auth.application.member.required
import org.around.ieuri.auth.application.member.required.dto.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.member.required.dto.SnsRevokeResult

/**
 * 탈퇴 시 provider 계정 연결을 해제하는 포트(SPEC-0012 R8~R11).
 * - Apple·Google은 저장된 refresh token, Kakao는 어드민 키 + providerUserId로 해제한다.
 * - 구현은 예외를 던지지 않는다. 실패는 반환값으로 알리고 탈퇴는 계속된다(R11).
 */
interface SnsTokenRevokerPort {
    fun revoke(command: RevokeSnsConnectionCommand): SnsRevokeResult
}
