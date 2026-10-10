package org.around.ieuri.tourism.adapter.event.listener

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.tourism.adapter.config.TourismAsyncConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCommandUseCase
import org.around.ieuri.tourism.application.footprint.provided.FootprintCommandUseCase
import org.around.ieuri.tourism.application.relation.provided.MapRelationCommandUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트(커밋 후)를 받아 애그리게이트마다 자기 데이터를 지우게 한다(SPEC-0011 R17, SPEC-0030 §9.1). 메서드 하나가 애그리게이트 하나다.
 * - 탈퇴 응답을 붙잡지 않게 tourism executor에서 돈다. 각 유스케이스가 자기 트랜잭션(REQUIRES_NEW)을 연다.
 * - 각 메서드가 예외를 삼키고 로그만 남긴다 — 한 애그리게이트의 실패가 나머지를 막지 않게. 재시도하지 않는다.
 * - 클래스명에 도메인 접두어를 유지한다 — 빈 이름이 겹치면 기동이 실패한다.
 */
@Component
class TourismMemberWithdrawnListener(
    private val catalogCommandUseCase: SpotCatalogCommandUseCase,
    private val footprintCommandUseCase: FootprintCommandUseCase,
    private val relationCommandUseCase: MapRelationCommandUseCase,
) {
    @Async(TourismAsyncConfig.WITHDRAWAL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseReactions(event: MemberWithdrawn) = erase(event, "reactions") { catalogCommandUseCase.eraseMemberContent(event.memberId) }

    /** 방문 기록은 개인정보 성격이 짙다. 공유 링크는 폐기가 아니라 삭제한다. */
    @Async(TourismAsyncConfig.WITHDRAWAL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseFootprint(event: MemberWithdrawn) = erase(event, "footprint") { footprintCommandUseCase.eraseMemberContent(event.memberId) }

    /** 관계는 양방향이라 상대 목록의 이 회원까지 지운다. */
    @Async(TourismAsyncConfig.WITHDRAWAL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseRelations(event: MemberWithdrawn) = erase(event, "relations") { relationCommandUseCase.eraseMemberContent(event.memberId) }

    /** 식별자와 건수만 남긴다(POL-013). 시군구 코드는 남기지 않는다. */
    private fun erase(event: MemberWithdrawn, target: String, step: () -> Int) {
        runCatching(step)
            .onSuccess { log.info("tourism {} cleared for withdrawn member {}: rows={}", target, event.memberId, it) }
            .onFailure { log.error("failed to clear tourism {} for withdrawn member {}", target, event.memberId, it) }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TourismMemberWithdrawnListener::class.java)
    }
}
