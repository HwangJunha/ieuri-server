package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand

/** 확인 결과. [failureReason]은 가입 검증과 같은 어휘(EXPIRED/CONSUMED/MAX_ATTEMPTS_EXCEEDED/CODE_MISMATCH), 성공 시 null. */
data class ConfirmEmailChangeResult(
    val changed: Boolean,
    val email: String?,
    val failureReason: String?,
)
