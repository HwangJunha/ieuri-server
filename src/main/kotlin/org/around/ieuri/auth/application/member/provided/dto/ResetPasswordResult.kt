package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand

/** 비밀번호 재설정 결과. 실패 사유는 코드 챌린지 실패 이름(EXPIRED/CONSUMED/…). */
data class ResetPasswordResult(
    val success: Boolean,
    val failureReason: String?,
)
