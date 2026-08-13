package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.ReactionDedupeResult
import org.around.ieuri.tourism.application.port.`in`.command.ReactionDedupeUseCase
import org.around.ieuri.tourism.application.port.out.persistence.ReactionDedupePort
import org.around.ieuri.tourism.domain.specification.ReactionDedupeRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요 dedupe 유스케이스 구현(SPEC-0019 §4.2).
 *
 * 남길 행을 고르는 규칙은 도메인이 소유한다([ReactionDedupeRule]) — 이 서비스는 대상을 모아
 * 규칙에 넘기고 결과를 지우는 순서만 담당한다.
 *
 * **규칙이 결정적이므로 중단했다 재실행해도 같은 행이 남는다.** 그것이 스펙이 동률 규칙
 * (`updated_at` 최신 → 동률 시 `id` 큰 쪽)을 요구한 이유다.
 */
@Service
class ReactionDedupeService(
    private val dedupePort: ReactionDedupePort,
) : ReactionDedupeUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun dedupe(): ReactionDedupeResult {
        val conflicting = dedupePort.findConflicting(DEDUPE_BATCH_SIZE)
        if (conflicting.isEmpty()) return ReactionDedupeResult(inspected = 0, deleted = 0)

        val plan = ReactionDedupeRule.plan(conflicting)
        val deleted = if (plan.deletedIds.isEmpty()) 0 else dedupePort.deleteByIds(plan.deletedIds)

        log.info(
            "reaction dedupe: inspected={} survivors={} deleted={}",
            conflicting.size, plan.survivors.size, deleted,
        )
        return ReactionDedupeResult(inspected = conflicting.size, deleted = deleted)
    }

    private companion object {
        /** 한 회차에 검사할 충돌 행 수. 전량을 메모리에 올리지 않기 위한 분할이다. */
        const val DEDUPE_BATCH_SIZE = 10_000
    }
}
