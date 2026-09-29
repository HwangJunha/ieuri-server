package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.out.client.EmailSenderPort
import org.around.ieuri.common.logging.maskEmail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * JavaMailSender 기반 SMTP 발송(multipart/alternative: HTML + 평문). 벤더는 설정으로만 정해진다.
 * - `fromAddress`가 빈 문자열이면 From 헤더를 설정하지 않는다.
 * - 실패해도 예외를 전파하지 않고 로깅만 한다(AFTER_COMMIT 경로). 재시도하지 않아 레이트 리밋에 걸린 메일은 유실된다.
 */
@Component
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    @Value("\${ieuri.mail.from:}") private val fromAddress: String,
) : EmailSenderPort {

    private val log = LoggerFactory.getLogger(SmtpEmailSender::class.java)

    override fun sendVerificationCode(toEmail: String, code: String) {
        send(toEmail, verificationCodeMail(code), "검증 코드")
    }

    override fun sendPasswordResetCode(toEmail: String, code: String) {
        send(toEmail, passwordResetCodeMail(code), "비밀번호 재설정 코드")
    }

    private fun send(toEmail: String, mail: VerificationCodeMail, purpose: String) {
        try {
            val message = mailSender.createMimeMessage()
            // multipart=true여야 평문 대체본을 함께 실을 수 있다.
            val helper = MimeMessageHelper(message, true, Charsets.UTF_8.name())
            if (fromAddress.isNotBlank()) helper.setFrom(fromAddress)
            helper.setTo(toEmail)
            helper.setSubject(mail.subject)
            // 인자 순서가 (평문, HTML)이다 — 뒤바뀌면 HTML 원문이 그대로 보인다.
            helper.setText(mail.plainText, mail.html)
            mailSender.send(message)
        } catch (ex: Exception) {
            log.warn(
                "{} 메일 발송 실패 [{}] (to={}): {}",
                purpose,
                classifySendFailure(ex).name,
                maskEmail(toEmail),
                ex.message,
            )
        }
    }
}
