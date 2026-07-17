package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.LifecycleResult
import org.around.ieuri.auth.application.port.`in`.command.SuspendMemberUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.event.MemberSuspended
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 정지(관리자). 상태만 SUSPENDED로 바꾸고 개인정보는 보존한다. refresh 전량 폐기 + 감사·이벤트.
 * 상태 전이 불변식은 도메인(Member.suspend)에서 강제한다.
 */
@Service
class SuspendMemberHandler(
    private val memberRepository: MemberRepositoryPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : SuspendMemberUseCase {

    @Transactional
    override fun handle(command: SuspendMemberCommand): LifecycleResult {
        val now = clock.instant()
        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        member.suspend(command.reason, command.suspendedUntil, now)
        memberRepository.updateStatus(member)
        refreshTokenStore.revokeAllByMember(command.memberId)

        auditLog.record(
            memberId = command.memberId,
            eventType = "MEMBER_SUSPENDED",
            payload = mapOf("reason" to command.reason, "suspendedUntil" to command.suspendedUntil?.toString()),
        )
        eventPublisher.publish(MemberSuspended(command.memberId, command.reason, command.suspendedUntil, now))
        return LifecycleResult(success = true)
    }
}
