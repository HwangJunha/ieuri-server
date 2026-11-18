package org.around.ieuri.auth.application.member.provided.dto
import java.time.Instant

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
