package org.around.ieuri.tourism.application.relation.required.dto

import java.time.Instant

/** 한 상대와의 겹침 계산 결과. 닉네임은 auth에 따로 묻는다(`MapOwnerLookupPort`). */
data class MapRelationOverlap(
    val counterpartId: Long,
    val overlappingCellIds: List<Long>,
    val theirVisitedCount: Int,
    val createdAt: Instant,
)
