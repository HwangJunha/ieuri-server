package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeResult
import org.around.ieuri.auth.application.member.provided.dto.EmailChangeRequested
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand

/**
 * 이메일 변경 유스케이스(SPEC-0011 R7·R8): 요청(새 주소로 코드 발송) → 확인 → 재발송.
 * - 코드 규칙은 가입 검증의 `VerificationPolicy`를 공유한다.
 */
interface EmailChangeUseCase {
    /** 새 주소로 코드를 보낸다. 통과 전까지 회원의 이메일은 기존 값이다. */
    fun changeEmail(command: ChangeEmailCommand): EmailChangeRequested

    /** 코드 확인. 통과하면 이메일이 교체되고, EMAIL 회원은 로그인 식별자도 함께 갱신된다(R8). */
    fun confirmEmailChange(command: ConfirmEmailChangeCommand): ConfirmEmailChangeResult

    /** 코드 재발송. 쿨다운은 이메일 변경 용도로만 센다. */
    fun resendEmailChange(command: ResendEmailChangeCommand): EmailChangeRequested
}
