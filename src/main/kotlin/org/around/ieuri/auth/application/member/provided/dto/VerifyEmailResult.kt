package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand

data class VerifyEmailResult(
    val verified: Boolean,
    /** 실패 사유(도메인 VerificationFailureReason 이름). 성공 시 null. */
    val failureReason: String?,
)
