package org.around.ieuri.auth.infrastructure.adapter.out.event

import org.around.ieuri.auth.application.event.PasswordResetRequested
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.port.out.client.EmailSenderPort
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.stereotype.Component

/**
 * 코드 메일 발송 트리거. 가입/재발송/비밀번호 재설정 트랜잭션이 커밋된 뒤에만 발송하여
 * 롤백 시 메일이 나가지 않도록 한다(AFTER_COMMIT).
 */
@Component
class VerificationEmailListener(
    private val emailSender: EmailSenderPort,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: VerificationEmailRequested) {
        emailSender.sendVerificationCode(event.toEmail, event.code)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: PasswordResetRequested) {
        emailSender.sendPasswordResetCode(event.toEmail, event.code)
    }
}
