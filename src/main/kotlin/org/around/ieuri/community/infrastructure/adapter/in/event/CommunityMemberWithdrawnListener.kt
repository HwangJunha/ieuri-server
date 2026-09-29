package org.around.ieuri.community.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.community.application.port.`in`.command.MemberDataEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트(커밋 후)를 받아 커뮤니티 데이터를 지운다(SPEC-0011 R17).
 * - 예외를 삼키고 로그만 남긴다 — 던지면 같은 이벤트의 다른 도메인 리스너까지 막힌다.
 * - 클래스명에 도메인 접두어를 유지한다 — 빈 이름이 겹치면 기동이 실패한다.
 */
@Component
class CommunityMemberWithdrawnListener(
    private val memberDataEraseUseCase: MemberDataEraseUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberDataEraseUseCase.eraseMemberData(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase community data for withdrawn member {}", event.memberId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunityMemberWithdrawnListener::class.java)
    }
}
