package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.MemberWithdrawalUseCase
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.application.member.required.SnsTokenRevokerPort
import org.around.ieuri.auth.application.member.required.dto.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.member.required.dto.SnsRevokeResult
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.springframework.stereotype.Service

/**
 * 탈퇴(본인). 트랜잭션을 갖지 않는다 — 외부 호출(provider·세션 저장소)이 끝난 뒤 삭제만 한 트랜잭션으로 한다.
 * - provider 해제는 삭제보다 먼저다. 해제용 토큰이 회원 행에 있어 삭제 뒤에는 읽을 수 없다. 해제 실패는 탈퇴를 막지 않는다(R11).
 * - 세션 폐기도 삭제보다 먼저다. 폐기가 실패하면 아무것도 지우지 않는다.
 */
@Service
class MemberWithdrawalService(
    private val members: MemberCommandUseCase,
    private val snsTokenRevoker: SnsTokenRevokerPort,
    private val sessions: MemberSessionPort,
    private val eventPublisher: MemberEventPublisherPort,
) : MemberWithdrawalUseCase {

    override fun withdraw(memberId: Long, ipAddress: String?): LifecycleResult {
        val target = members.withdrawalTarget(memberId)
        val revokeResult = snsTokenRevoker.revoke(
            RevokeSnsConnectionCommand(
                provider = target.provider,
                providerUserId = target.providerUserId,
                providerRefreshToken = target.providerRefreshToken,
            ),
        )
        sessions.revokeAll(memberId)
        members.erase(memberId, target.provider, (revokeResult as? SnsRevokeResult.Failed)?.reason)
        // 접속기록(기준 제8조). 계정·개인정보 삭제라 남겨야 하는 수행업무다.
        eventPublisher.publish(MemberAccessed(memberId, AccessType.WITHDRAW, ipAddress))
        return LifecycleResult(success = true)
    }
}
