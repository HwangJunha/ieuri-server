package org.around.ieuri.tourism.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트를 받아 이 도메인의 회원 데이터를 지운다(SPEC-0011 R17, SPEC-0030 §9.1).
 * - 반응과 전국 지도 데이터를 각각 독립된 트랜잭션으로 지운다 — 한쪽이 실패해도 다른 쪽은 지워진다.
 * - `AFTER_COMMIT`으로 실행되며 예외를 삼키고 기록만 남긴다.
 * - 클래스 이름의 도메인 접두는 빈 이름 충돌을 피하기 위함이다.
 */
@Component
class TourismMemberWithdrawnListener(
    private val memberReactionEraseUseCase: MemberReactionEraseUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberReactionEraseUseCase.eraseMemberReactions(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase tourism reactions for withdrawn member {}", event.memberId, e)
        }
        try {
            memberReactionEraseUseCase.eraseMemberMapData(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase tourism map data for withdrawn member {}", event.memberId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TourismMemberWithdrawnListener::class.java)
    }
}
