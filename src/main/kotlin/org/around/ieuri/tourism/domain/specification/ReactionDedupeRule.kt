package org.around.ieuri.tourism.domain.specification

import java.time.Instant

/**
 * 한 회원이 같은 카탈로그에 남긴 반응이 여럿일 때 현재 의사를 고르는 규칙(SPEC-0019 R-10).
 * - 이 규칙을 적용한 곳은 마이그레이션 V53이다. 규칙은 결정적이어야 한다.
 */
object ReactionDedupeRule {

    /** 충돌하는 반응들 중 남길 하나. `updated_at`이 최신인 행이며, 동률이면 `id`가 큰 행이다. */
    fun survivor(conflicting: List<ReactionRow>): ReactionRow {
        require(conflicting.isNotEmpty()) { "dedupe 대상이 비어 있습니다." }
        return conflicting.maxWith(compareBy<ReactionRow> { it.updatedAt }.thenBy { it.id })
    }

    /** 같은 `(memberId, catalogId)`끼리 묶어 남길 행과 지울 행을 가른다. 충돌하지 않는 행은 지워지지 않는다. */
    fun plan(rows: List<ReactionRow>): ReactionDedupePlan {
        val grouped = rows.groupBy { it.memberId to it.catalogId }
        val survivors = grouped.values.map(::survivor)
        val survivorIds = survivors.mapTo(mutableSetOf()) { it.id }

        return ReactionDedupePlan(
            survivors = survivors,
            deletedIds = rows.map { it.id }.filterNot { it in survivorIds },
        )
    }
}

/** dedupe 판정에 필요한 값만 담은 반응 한 행. */
data class ReactionRow(
    val id: Long,
    val memberId: Long,
    val catalogId: Long,
    val updatedAt: Instant,
)

/** dedupe 결과. [deletedIds]를 지우면 `(memberId, catalogId)` 중복이 남지 않는다. */
data class ReactionDedupePlan(
    val survivors: List<ReactionRow>,
    val deletedIds: List<Long>,
)
