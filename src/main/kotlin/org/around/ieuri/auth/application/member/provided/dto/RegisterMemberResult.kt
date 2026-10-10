package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import java.util.UUID

/** 가입 결과. 미검증이면 verificationPublicId(검증 핸들)가 채워진다. member_id는 노출하지 않는다. */
data class RegisterMemberResult(
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
)
