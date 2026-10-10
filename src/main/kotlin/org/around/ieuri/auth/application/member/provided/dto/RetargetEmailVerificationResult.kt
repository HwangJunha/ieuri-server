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

/** 인증 주소 재입력 결과. 새 주소로 재발급된 검증 핸들과 만료 시각. */
data class RetargetEmailVerificationResult(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)
