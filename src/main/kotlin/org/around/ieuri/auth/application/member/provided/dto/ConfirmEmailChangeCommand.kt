package org.around.ieuri.auth.application.member.provided.dto
import java.util.UUID

/** 이메일 변경 코드 확인(R7). 통과하면 회원의 이메일이 교체된다. */
data class ConfirmEmailChangeCommand(
    val verificationPublicId: UUID,
    val code: String,
) {
    init {
        require(code.matches(Regex("^\\d{6}$"))) { "코드는 6자리 숫자여야 합니다." }
    }
}
