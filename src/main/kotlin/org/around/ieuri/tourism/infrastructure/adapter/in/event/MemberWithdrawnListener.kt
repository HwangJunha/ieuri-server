package org.around.ieuri.tourism.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트를 받아 좋아요/싫어요를 지운다(SPEC-0011 R17).
 *
 * 커뮤니티 리스너와 같은 규약이다 — `AFTER_COMMIT`으로 실행되고 **예외를 삼킨다.**
 * 한 도메인의 삭제 실패가 다른 도메인의 삭제를 막지 않아야 하며, 커밋 뒤라 회원 삭제를
 * 되돌릴 수도 없다. 실패는 기록만 남긴다.
 */
@Component
class MemberWithdrawnListener(
    private val memberReactionEraseUseCase: MemberReactionEraseUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberReactionEraseUseCase.eraseMemberReactions(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase tourism reactions for withdrawn member {}", event.memberId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberWithdrawnListener::class.java)
    }
}
