package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberLifecycleUseCase
import org.around.ieuri.auth.application.member.provided.dto.EraseMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.event.MemberReactivated
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.around.ieuri.auth.domain.member.event.SnsUnlinkFailed
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 정지·복구(관리자)와 탈퇴의 DB 단계. 상태 전이 규칙은 도메인이 판정한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberLifecycleService(
    private val memberRepository: MemberRepository,
    private val sessions: MemberSessionPort,
    private val eventPublisher: MemberEventPublisherPort,
    private val clock: Clock,
) : MemberLifecycleUseCase {

    /**
     * 상태만 SUSPENDED로 바꾸고 개인정보는 보존한다.
     * - 세션 폐기는 트랜잭션 안에 둔다. 정지가 정해진 뒤에만 끊어야 하고, 폐기가 실패하면 정지도 되돌아가야 한다.
     */
    override fun suspend(command: SuspendMemberCommand): LifecycleResult {
        val now = clock.instant()
        val suspended = load(command.memberId).suspend(command.reason, command.suspendedUntil, now)
        memberRepository.saveStatus(suspended)
        sessions.revokeAll(command.memberId)
        eventPublisher.publish(MemberSuspended(command.memberId, command.reason, command.suspendedUntil, now))
        return LifecycleResult(success = true)
    }

    override fun reactivate(command: ReactivateMemberCommand): LifecycleResult {
        memberRepository.saveStatus(load(command.memberId).reactivate())
        eventPublisher.publish(MemberReactivated(command.memberId, clock.instant()))
        return LifecycleResult(success = true)
    }

    override fun withdrawalTarget(memberId: Long): WithdrawalTargetView {
        val member = load(memberId)
        return WithdrawalTargetView(
            provider = member.provider,
            providerUserId = member.providerUserId.value,
            providerRefreshToken = memberRepository.findProviderRefreshToken(memberId),
        )
    }

    /** 행 삭제(hard delete). [MemberWithdrawn]은 커밋 뒤 다른 context로 나간다(SPEC-0011 R17). */
    override fun erase(command: EraseMemberCommand) {
        memberRepository.delete(command.memberId)
        // 해제 실패는 provider와 사유만 남긴다. 토큰·이메일은 남기지 않는다(R11).
        command.unlinkFailureReason?.let { eventPublisher.publish(SnsUnlinkFailed(command.memberId, command.provider, it)) }
        eventPublisher.publish(MemberWithdrawn(command.memberId, command.provider, clock.instant()))
    }

    private fun load(memberId: Long): Member = memberRepository.findById(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
}
