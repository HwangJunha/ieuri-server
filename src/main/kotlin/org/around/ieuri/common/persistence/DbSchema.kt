package org.around.ieuri.common.persistence

/**
 * 컨텍스트별 DB 스키마 이름(SPEC-0023). `@Table(schema = ...)`이 이 상수만 쓴다.
 *
 * **리터럴로 흩뿌리지 않는 이유는 오타의 발견 시점이다.** 잘못된 스키마 이름은 컴파일을
 * 통과하고 `ddl-auto: validate`가 부팅에서야 잡는데, 그때 어느 엔티티인지 찾는 비용이 크다.
 *
 * `TOUR`와 `CATEGORY`는 둘 다 tourism 패키지의 엔티티가 쓴다 — 패키지 위치로 구분되지
 * 않으므로 엔티티마다 확인해야 한다. 배치 메타 테이블(`batch`)은 Spring Batch가 소유하며
 * JPA 엔티티가 없어 여기 없다.
 */
object DbSchema {
    const val AUTH = "auth"
    const val COMMUNITY = "community"
    const val TOUR = "tour"
    const val CATEGORY = "category"
}
