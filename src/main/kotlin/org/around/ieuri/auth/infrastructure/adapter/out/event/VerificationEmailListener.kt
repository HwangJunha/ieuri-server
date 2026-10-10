package org.around.ieuri.auth.infrastructure.adapter.out.event

import org.around.ieuri.auth.application.member.PasswordResetRequested
import org.around.ieuri.auth.application.member.VerificationEmailRequested
import org.around.ieuri.auth.application.member.required.EmailSenderPort
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 코드 메일 발송 트리거. 트랜잭션 커밋 후에만 발송해 롤백 시 메일이 나가지 않는다(AFTER_COMMIT). */
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
