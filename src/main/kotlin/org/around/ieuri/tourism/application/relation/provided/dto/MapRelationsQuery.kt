package org.around.ieuri.tourism.application.relation.provided.dto

/** 관계 목록 조회 입력. 정렬은 `created_at` 내림차순 고정이다(§9). */
data class MapRelationsQuery(
    val first: Int,
    val after: String?,
)
