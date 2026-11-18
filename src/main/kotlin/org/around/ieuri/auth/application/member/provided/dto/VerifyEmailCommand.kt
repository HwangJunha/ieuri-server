package org.around.ieuri.auth.application.member.provided.dto
import java.util.UUID

/** 이메일 검증 커맨드. 가입 응답으로 받은 검증 핸들(publicId) + 6자리 코드. */
data class VerifyEmailCommand(
    val verificationPublicId: UUID,
    val code: String,
) {
    init {
        require(code.matches(Regex("^\\d{6}$"))) { "코드는 6자리 숫자여야 합니다." }
    }
}
