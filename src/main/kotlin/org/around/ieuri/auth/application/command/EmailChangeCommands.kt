package org.around.ieuri.auth.application.command

import java.util.UUID

/** 이메일 변경 요청(SPEC-0011 R7). 새 주소로 6자리 코드를 보낸다. */
data class ChangeEmailCommand(
    val memberId: Long,
    val newEmail: String,
)

/** 이메일 변경 코드 확인(R7). 통과하면 회원의 이메일이 교체된다. */
data class ConfirmEmailChangeCommand(
    val memberId: Long,
    val verificationPublicId: UUID,
    val code: String,
) {
    init {
        require(code.matches(Regex("^\\d{6}$"))) { "코드는 6자리 숫자여야 합니다." }
    }
}

/** 이메일 변경 코드 재발송(R7). 쿨다운은 이메일 변경 용도로만 센다. */
data class ResendEmailChangeCommand(
    val memberId: Long,
    val verificationPublicId: UUID,
)
