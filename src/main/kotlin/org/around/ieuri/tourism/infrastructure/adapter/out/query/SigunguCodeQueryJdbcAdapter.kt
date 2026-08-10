package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.persistence.SigunguCodeQueryPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 등록된 시군구 코드 집합 조회(SPEC-0015 R9).
 *
 * `sigungu.boundary`가 `geography(MultiPolygon,4326)`이라 JPA 엔티티에 매핑되어 있지 않고,
 * 그래서 이 테이블을 읽는 질의는 생 SQL로 남는다([CongestionAreaQueryAdapter]와 같은 선례).
 *
 * 배치가 실행당 한 번만 부른다 — 252행이라 전량을 메모리에 올려 대조하는 편이 행마다 EXISTS를
 * 던지는 것보다 훨씬 싸다.
 */
@Component
class SigunguCodeQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SigunguCodeQueryPort {

    @Transactional(readOnly = true)
    override fun findAllCodes(): Set<String> =
        jdbcTemplate.queryForList("SELECT code FROM sigungu", String::class.java).filterNotNull().toSet()
}
