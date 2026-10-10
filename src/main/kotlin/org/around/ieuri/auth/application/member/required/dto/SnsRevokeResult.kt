package org.around.ieuri.auth.application.member.required.dto

/** 해제 시도 결과. 실패 사유는 감사 로그용이며 토큰·이메일을 담지 않는다(R11). */
sealed interface SnsRevokeResult {
    /** 해제 성공. */
    data object Revoked : SnsRevokeResult

    /** 해제 대상이 아님(EMAIL 회원, 또는 토큰이 필요한데 저장된 토큰이 없는 회원). */
    data object Skipped : SnsRevokeResult

    /** 해제 실패. 탈퇴는 계속 진행된다. */
    data class Failed(val reason: String) : SnsRevokeResult
}
