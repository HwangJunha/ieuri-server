package org.around.ieuri.common.persistence

/**
 * 컨텍스트별 DB 스키마 이름(SPEC-0023). `@Table(schema = ...)`이 이 상수만 쓴다.
 * - `TOUR`와 `CATEGORY`는 둘 다 tourism 패키지의 엔티티가 쓴다 — 엔티티마다 확인한다.
 */
object DbSchema {
    const val AUTH = "auth"
    const val COMMUNITY = "community"
    const val TOUR = "tour"
    const val CATEGORY = "category"
    const val ALARM = "alarm"
}
