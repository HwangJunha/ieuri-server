package org.around.ieuri.tourism.application.relation

/** 지도 관계 조회 정책값(설정에서 주입, POL-044). [maxPageSize] 초과 first는 클램프다(POL-018). */
data class MapRelationPolicy(
    val maxPageSize: Int,
)
