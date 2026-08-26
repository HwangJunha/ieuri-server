package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.out.client.EmailSenderPort
import org.around.ieuri.common.logging.maskEmail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * JavaMailSender를 통한 SMTP 발송 구현. 벤더(현재 OCI Email Delivery)는 설정으로만 정해지며
 * 이 클래스는 벤더를 알지 않는다.
 *
 * 본문은 HTML과 평문을 함께 담은 multipart/alternative다 — HTML을 막아 둔 클라이언트에는
 * 평문이 보인다. 템플릿은 [VerificationCodeMail]이 만든다.
 *
 * `fromAddress`가 빈 문자열이면 From 헤더를 설정하지 않는다.
 * 발송은 AFTER_COMMIT 리스너에서 트리거되므로 **실패해도 예외를 전파하지 않고 로깅만 한다** —
 * 이미 커밋된 트랜잭션이라 롤백되지 않으면서 호출 스택만 오염된다. 재시도하지 않으므로
 * 레이트 리밋에 걸린 메일은 유실된다(SPEC-0016 R7).
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
