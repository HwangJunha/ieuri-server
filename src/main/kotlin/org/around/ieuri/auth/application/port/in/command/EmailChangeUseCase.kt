package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.ChangeEmailCommand
import org.around.ieuri.auth.application.command.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.command.ResendEmailChangeCommand
import java.time.Instant
import java.util.UUID

/**
 * 이메일 변경 유스케이스(SPEC-0011 R7·R8). 가입 유형과 무관하게 모든 회원이 쓴다.
 *
 * 가입 검증과 같은 3단계다 — 요청(코드 발송) → 확인 → 재발송. 코드 규칙(만료 10분·시도 5회·
 * 쿨다운 60초)도 `VerificationPolicy`를 그대로 공유한다. 다른 것은 **코드를 보내는 주소**와
 * **통과 후 처리**뿐이다: 아직 회원의 것이 아닌 새 주소로 보내고, 통과하면 이메일을 교체한다.
 */
interface EmailChangeUseCase {
    /** 새 주소로 코드를 보낸다. 통과 전까지 회원의 이메일은 기존 값이다. */
    fun changeEmail(command: ChangeEmailCommand): EmailChangeRequested

    /** 코드 확인. 통과하면 이메일이 교체되고, EMAIL 회원은 로그인 식별자도 함께 갱신된다(R8). */
    fun confirmEmailChange(command: ConfirmEmailChangeCommand): ConfirmEmailChangeResult

    /** 코드 재발송. 쿨다운은 이메일 변경 용도로만 센다. */
    fun resendEmailChange(command: ResendEmailChangeCommand): EmailChangeRequested
}

/** 코드 발송 결과. 핸들과 만료만 돌려주고 코드는 메일로만 나간다. */
data class EmailChangeRequested(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)

/**
 * 확인 결과. [failureReason]은 가입 검증과 같은 어휘를 쓴다
 * (EXPIRED/CONSUMED/MAX_ATTEMPTS_EXCEEDED/CODE_MISMATCH). 성공 시 null이다.
 */
data class ConfirmEmailChangeResult(
    val changed: Boolean,
    val email: String?,
    val failureReason: String?,
)
