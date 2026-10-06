package org.around.ieuri.tourism.application.relation.provided.dto

import java.time.Instant

/**
 * 관계 목록 1건(SPEC-0030 확장).
 * - 겹침은 저장값이 아니라 조회 시점에 재계산된 값이다.
 */
data class MapRelationView(
    val memberId: Long,
    val nickname: String,
    /** 겹친 칸 수. `overlappingCellIds.size`와 같다. */
    val overlappingCount: Int,
    val overlappingCellIds: List<Long>,
    /** 상대가 방문한 전체 칸 수. */
    val theirVisitedCount: Int,
    val createdAt: Instant,
)

/** 관계 목록 조회 입력. 정렬은 `created_at` 내림차순 고정이다(§9). */
data class MapRelationsQuery(
    val first: Int,
    val after: String?,
)

/** 한 상대와의 겹침 계산 결과. 닉네임은 auth에 따로 묻는다(`MapOwnerLookupPort`). */
data class MapRelationOverlap(
    val counterpartId: Long,
    val overlappingCellIds: List<Long>,
    val theirVisitedCount: Int,
    val createdAt: Instant,
)
