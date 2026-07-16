package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.out.client.EmailSenderPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Gmail SMTP 발송 구현(개발/임시). 발송부는 EmailSenderPort로 추상화되어 있어
 * 도메인 확보 후 Resend 구현으로 교체할 수 있다.
 * From은 개발용 Gmail 주소이며 운영에선 noreply@ieuri.kr로 교체한다.
 * 발송 실패는 가입/검증 트랜잭션(이미 커밋됨)에 영향을 주지 않도록 로깅만 한다.
 */
@Component
class GmailEmailSender(
    private val mailSender: JavaMailSender,
    @Value("\${ieuri.mail.from:}") private val fromAddress: String,
) : EmailSenderPort {

    private val log = LoggerFactory.getLogger(GmailEmailSender::class.java)

    override fun sendVerificationCode(toEmail: String, code: String) {
        try {
            val message = SimpleMailMessage().apply {
                if (fromAddress.isNotBlank()) setFrom(fromAddress)
                setTo(toEmail)
                subject = "[이으리] 이메일 인증 코드"
                text = "이메일 인증 코드는 [$code] 입니다. 요청하지 않으셨다면 이 메일을 무시하세요."
            }
            mailSender.send(message)
        } catch (ex: Exception) {
            log.warn("검증 코드 메일 발송 실패 (to={}): {}", toEmail, ex.message)
        }
    }
}
