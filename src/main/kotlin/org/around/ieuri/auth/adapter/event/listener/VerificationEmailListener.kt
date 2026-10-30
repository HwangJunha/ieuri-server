package org.around.ieuri.auth.adapter.event.listener
import org.around.ieuri.auth.application.member.PasswordResetRequested
import org.around.ieuri.auth.application.member.VerificationEmailRequested
import org.around.ieuri.auth.application.member.provided.VerificationMailUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 코드 메일 발송 트리거. 트랜잭션 커밋 후에만 발송해 롤백 시 메일이 나가지 않는다(AFTER_COMMIT). */
@Component
class VerificationEmailListener(
    private val verificationMailUseCase: VerificationMailUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: VerificationEmailRequested) {
        verificationMailUseCase.sendVerificationCode(event.toEmail, event.code)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: PasswordResetRequested) {
        verificationMailUseCase.sendPasswordResetCode(event.toEmail, event.code)
    }
}
