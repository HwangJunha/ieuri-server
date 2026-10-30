package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.VerificationMailUseCase
import org.around.ieuri.auth.application.member.required.EmailSenderPort
import org.springframework.stereotype.Service

/** 인증 코드 메일 발송. 외부 호출뿐이라 트랜잭션이 없다. */
@Service
class VerificationMailService(
    private val emailSender: EmailSenderPort,
) : VerificationMailUseCase {

    override fun sendVerificationCode(toEmail: String, code: String) = emailSender.sendVerificationCode(toEmail, code)

    override fun sendPasswordResetCode(toEmail: String, code: String) = emailSender.sendPasswordResetCode(toEmail, code)
}
