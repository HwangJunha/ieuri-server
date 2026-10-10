package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordResult

/** 비밀번호 재설정: 코드 발송 요청 → 코드 확인과 교체. */
interface PasswordResetUseCase {
    /** 이메일로 재설정 코드를 보낸다. */
    fun requestPasswordReset(command: RequestPasswordResetCommand)

    /** 코드가 맞으면 새 비밀번호로 바꾸고 로그인 세션을 모두 끊는다. */
    fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult
}
