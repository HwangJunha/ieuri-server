package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import java.time.Instant
import java.util.UUID

data class ResendVerificationResult(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)
