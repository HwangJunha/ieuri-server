package org.around.ieuri.common.persistence

/**
 * 컨텍스트별 DB 스키마 이름(SPEC-0023). JPA 엔티티의 `@Table(schema = ...)`이 이 상수만 쓴다.
 * - Exposed로 옮긴 context(community·tourism)는 `Table` 선언이 `스키마.테이블` 이름을 직접 갖는다.
 */
object DbSchema {
    const val AUTH = "auth"
    const val COMMUNITY = "community"
    const val ALARM = "alarm"
}
