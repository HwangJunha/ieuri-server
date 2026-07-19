package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.LifecycleResult
import org.around.ieuri.auth.application.port.`in`.command.MemberLifecycleUseCase
import org.around.ieuri.auth.application.port.out.client.SnsTokenRevokerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.event.MemberReactivated
import org.around.ieuri.auth.domain.event.MemberSuspended
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 회원 생명주기 계열 서비스. 탈퇴(본인)·정지/복구(관리자)를 한 서비스로 묶어 관리한다.
 * 상태 전이 불변식은 도메인(Member.suspend/reactivate)에서 강제하고, 여기선 오케스트레이션만 담당한다.
 */
@Service
class MemberLifecycleService(
    private val memberRepository: MemberRepositoryPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val snsTokenRevoker: SnsTokenRevokerPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : MemberLifecycleUseCase {

    /**
     * 탈퇴(본인). 회원과 소유 데이터(프로필·동의이력·이메일검증)를 **행 삭제**한다(hard delete).
     * SNS provider 토큰 폐기 시도 → 삭제 → refresh 전량 폐기 → 감사 기록(비-PII)·이벤트.
     */
    @Transactional
    override fun withdraw(command: WithdrawMemberCommand): LifecycleResult {
        val now = clock.instant()
        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        snsTokenRevoker.revoke(member.provider, member.providerUserId.value)
        memberRepository.deleteMember(command.memberId)
        refreshTokenStore.revokeAllByMember(command.memberId)

        auditLog.record(
            memberId = command.memberId,
            eventType = "MEMBER_WITHDRAWN",
            payload = mapOf("provider" to member.provider.name),
        )
        eventPublisher.publish(MemberWithdrawn(command.memberId, now))
        return LifecycleResult(success = true)
    }

    /**
     * 정지(관리자). 상태만 SUSPENDED로 바꾸고 개인정보는 보존한다. refresh 전량 폐기 + 감사·이벤트.
     */
    @Transactional
    override fun suspend(command: SuspendMemberCommand): LifecycleResult {
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

    /**
     * 복구(관리자). SUSPENDED → ACTIVE(도메인 Member.reactivate가 상태를 강제). 감사·이벤트.
     */
    @Transactional
    override fun reactivate(command: ReactivateMemberCommand): LifecycleResult {
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
