package org.around.ieuri.auth.adapter.event.listener

import org.around.ieuri.auth.adapter.event.mapper.toAuditPayload
import org.around.ieuri.auth.application.audit.provided.AuditLogUseCase
import org.around.ieuri.auth.application.member.PasswordResetRequested
import org.around.ieuri.auth.domain.member.event.EmailChanged
import org.around.ieuri.auth.domain.member.event.EmailVerificationRetargeted
import org.around.ieuri.auth.domain.member.event.EmailVerified
import org.around.ieuri.auth.domain.member.event.MemberReactivated
import org.around.ieuri.auth.domain.member.event.MemberRegistered
import org.around.ieuri.auth.domain.member.event.MemberRoleGranted
import org.around.ieuri.auth.domain.member.event.MemberRoleRevoked
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.around.ieuri.auth.domain.member.event.PasswordResetCompleted
import org.around.ieuri.auth.domain.member.event.SnsUnlinkFailed
import org.around.ieuri.auth.domain.member.event.TermsAgreementChanged
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 회원에게 일어난 일을 감사 기록으로 남긴다. 회원 트랜잭션이 커밋된 뒤, 따로 커밋한다.
 * - 기록이 실패해도 이미 끝난 요청을 실패시키지 않는다. 경고 로그만 남는다.
 */
@Component
class MemberAuditListener(
    private val auditLog: AuditLogUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberRegistered) = record(event.memberId, "MEMBER_REGISTERED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: EmailVerified) = record(event.memberId, "EMAIL_VERIFIED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: EmailVerificationRetargeted) = record(event.memberId, "EMAIL_VERIFICATION_RETARGETED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: EmailChanged) = record(event.memberId, "EMAIL_CHANGED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: PasswordResetRequested) = record(event.memberId, "PASSWORD_RESET_REQUESTED", emptyMap())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: PasswordResetCompleted) = record(event.memberId, "PASSWORD_RESET", emptyMap())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: SnsUnlinkFailed) = record(event.memberId, "SNS_UNLINK_FAILED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) = record(event.memberId, "MEMBER_WITHDRAWN", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberSuspended) = record(event.memberId, "MEMBER_SUSPENDED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberReactivated) = record(event.memberId, "MEMBER_REACTIVATED", emptyMap())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberRoleGranted) = record(event.memberId, "MEMBER_ROLE_ASSIGNED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberRoleRevoked) = record(event.memberId, "MEMBER_ROLE_REVOKED", event.toAuditPayload())

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TermsAgreementChanged) = record(event.memberId, if (event.agreed) "TERMS_AGREED" else "TERMS_WITHDRAWN", event.toAuditPayload())

    private fun record(memberId: Long, eventType: String, payload: Map<String, Any?>) {
        try {
            auditLog.recordMemberAudit(memberId, eventType, payload)
        } catch (e: RuntimeException) {
            log.warn("회원 감사 기록에 실패했다: eventType={}", eventType, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MemberAuditListener::class.java)
    }
}
