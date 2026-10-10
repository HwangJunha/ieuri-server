package org.around.ieuri.auth.application.member
import org.around.ieuri.auth.application.member.provided.MemberLifecycleUseCase
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawMemberCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.DomainEventPublisherPort
import org.around.ieuri.auth.application.member.required.SnsTokenRevokerPort
import org.around.ieuri.auth.application.member.required.dto.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.member.required.dto.SnsRevokeResult
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.domain.member.event.MemberReactivated
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 회원 생명주기 서비스: 탈퇴(본인)·정지/복구(관리자). 상태 전이 불변식은 도메인이 강제한다. */
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
     * 탈퇴(본인): provider 연결 해제 → 행 삭제(hard delete) → refresh 전량 폐기 → 감사 기록·이벤트.
     * - 해제는 삭제보다 먼저다. 폐기용 토큰이 회원 행에 있어 삭제 후에는 읽을 수 없다.
     * - 해제 실패는 탈퇴를 막지 않는다(R11). [MemberWithdrawn]은 커밋 후 다른 도메인으로 나간다(SPEC-0011 R17).
     */
    @Transactional
    override fun withdraw(command: WithdrawMemberCommand): LifecycleResult {
        val now = clock.instant()
        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        val revokeResult = snsTokenRevoker.revoke(
            RevokeSnsConnectionCommand(
                provider = member.provider,
                providerUserId = member.providerUserId.value,
                providerRefreshToken = memberRepository.findProviderRefreshToken(command.memberId),
            ),
        )

        memberRepository.deleteMember(command.memberId)
        refreshTokenStore.revokeAllByMember(command.memberId)

        // 해제 실패는 provider와 회원 식별자로만 남긴다. 토큰·이메일은 남기지 않는다(R11).
        if (revokeResult is SnsRevokeResult.Failed) {
            auditLog.record(
                memberId = command.memberId,
                eventType = "SNS_UNLINK_FAILED",
                payload = mapOf("provider" to member.provider.name, "reason" to revokeResult.reason),
            )
        }

        auditLog.record(
            memberId = command.memberId,
            eventType = "MEMBER_WITHDRAWN",
            payload = mapOf("provider" to member.provider.name),
        )
        eventPublisher.publish(MemberWithdrawn(command.memberId, member.provider, now))
        return LifecycleResult(success = true)
    }

    /** 정지(관리자). 상태만 SUSPENDED로 바꾸고 개인정보는 보존한다. refresh 전량 폐기 + 감사·이벤트. */
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

    /** 복구(관리자). SUSPENDED → ACTIVE. 감사·이벤트. */
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
