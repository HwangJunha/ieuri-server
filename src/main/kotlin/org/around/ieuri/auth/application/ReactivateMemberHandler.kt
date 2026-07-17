package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.LifecycleResult
import org.around.ieuri.auth.application.port.`in`.command.ReactivateMemberUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.event.MemberReactivated
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 복구(관리자). SUSPENDED → ACTIVE(도메인 Member.reactivate가 상태를 강제). 감사·이벤트.
 */
@Service
class ReactivateMemberHandler(
    private val memberRepository: MemberRepositoryPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : ReactivateMemberUseCase {

    @Transactional
    override fun handle(command: ReactivateMemberCommand): LifecycleResult {
        val now = clock.instant()
        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        member.reactivate(now)
        memberRepository.updateStatus(member)

        auditLog.record(command.memberId, "MEMBER_REACTIVATED", emptyMap())
        eventPublisher.publish(MemberReactivated(command.memberId, now))
        return LifecycleResult(success = true)
    }
}
