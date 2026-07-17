package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.WithdrawMemberCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.LifecycleResult
import org.around.ieuri.auth.application.port.`in`.command.WithdrawMemberUseCase
import org.around.ieuri.auth.application.port.out.client.SnsTokenRevokerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 탈퇴(본인). 회원과 소유 데이터(프로필·동의이력·이메일검증)를 **행 삭제**한다(hard delete).
 * SNS provider 토큰 폐기 시도 → 삭제 → refresh 전량 폐기 → 감사 기록(비-PII)·이벤트.
 * 감사 로그는 FK가 없어 회원 삭제 후에도 보존된다.
 */
@Service
class WithdrawMemberHandler(
    private val memberRepository: MemberRepositoryPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val snsTokenRevoker: SnsTokenRevokerPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : WithdrawMemberUseCase {

    @Transactional
    override fun handle(command: WithdrawMemberCommand): LifecycleResult {
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
}
