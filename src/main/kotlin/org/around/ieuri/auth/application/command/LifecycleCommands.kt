package org.around.ieuri.auth.application.command

import java.time.Instant

/**
 * 탈퇴 커맨드. memberId는 클라이언트 입력이 아니라 accessToken으로 인증된 본인 식별자다.
 */
data class WithdrawMemberCommand(
    val memberId: Long,
)

/** 정지 커맨드(관리자). until이 null이면 무기한. */
data class SuspendMemberCommand(
    val memberId: Long,
    val reason: String,
    val suspendedUntil: Instant?,
) {
    init {
        require(reason.isNotBlank()) { "정지 사유는 필수입니다." }
    }
}

/** 복구 커맨드(관리자). */
data class ReactivateMemberCommand(
    val memberId: Long,
)
