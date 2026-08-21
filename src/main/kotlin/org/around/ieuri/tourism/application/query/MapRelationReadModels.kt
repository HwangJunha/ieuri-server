package org.around.ieuri.tourism.application.query

import java.time.Instant

/**
 * 관계 목록 1건(SPEC-0030 확장).
 *
 * **겹침은 조회 시점에 재계산된 값이다** — 저장돼 있지 않다. 서로 여행을 더 다니면
 * 다음 조회에서 늘어난다.
 */
data class MapRelationView(
    val memberId: Long,
    val nickname: String,
    /** 겹친 칸 수. `overlappingCellIds.size`와 같지만 클라이언트가 세지 않아도 되게 함께 준다. */
    val overlappingCount: Int,
    val overlappingCellIds: List<Long>,
    /** 상대가 방문한 전체 칸 수. 겹침 비율("6/20곳")을 보여줄 때 쓴다. */
    val theirVisitedCount: Int,
    val createdAt: Instant,
)

/** 관계 목록 조회 입력. 정렬은 `created_at` 내림차순 고정이다(§9). */
data class MapRelationsQuery(
    val first: Int,
    val after: String?,
)

/**
 * 한 상대와의 겹침 계산 결과. 목록 조회가 전원분을 한 번에 받는다.
 *
 * 닉네임이 없는 이유는 **다른 도메인에서 오기 때문이다** — 겹침은 tourism이 계산하고
 * 닉네임은 auth에 묻는다(`MapOwnerLookupPort`).
 */
data class MapRelationOverlap(
    val counterpartId: Long,
    val overlappingCellIds: List<Long>,
    val theirVisitedCount: Int,
    val createdAt: Instant,
)
