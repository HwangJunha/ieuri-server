package org.around.ieuri.auth.domain.member.event

import org.around.ieuri.auth.domain.member.Provider

/** 탈퇴 중 provider 연결 해제가 실패했다. 탈퇴는 그대로 진행된다(SPEC-0012 R11). */
data class SnsUnlinkFailed(
    val memberId: Long,
    val provider: Provider,
    val reason: String,
)
