package org.around.ieuri.tourism.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트를 받아 이 도메인의 회원 데이터를 지운다(SPEC-0011 R17, SPEC-0030 §9.1).
 *
 * 좋아요/싫어요와 전국 지도 데이터(방문 기록·공유 링크)를 **각각 독립된 트랜잭션으로** 지운다 —
 * 한쪽이 실패해도 다른 쪽은 지워져야 한다. 그래서 try가 둘이다.
 *
 * 클래스 이름에 도메인을 붙이는 이유는 커뮤니티 리스너와 빈 이름이 겹치지 않게 하기 위함이다 —
 * Spring은 단순 클래스명으로 빈 이름을 지어 패키지가 달라도 충돌한다.
 *
 * 그 밖에는 커뮤니티 리스너와 같은 규약이다 — `AFTER_COMMIT`으로 실행되고 **예외를 삼킨다.**
 * 한 도메인의 삭제 실패가 다른 도메인의 삭제를 막지 않아야 하며, 커밋 뒤라 회원 삭제를
 * 되돌릴 수도 없다. 실패는 기록만 남긴다.
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
