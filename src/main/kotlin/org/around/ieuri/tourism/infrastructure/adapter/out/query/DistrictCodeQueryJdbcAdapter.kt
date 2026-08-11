package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.persistence.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRegion
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 등록된 시군구 코드 집합 조회(SPEC-0015 R9).
 *
 * `district.boundary`가 `geography(MultiPolygon,4326)`이라 JPA 엔티티에 매핑되어 있지 않고,
 * 그래서 이 테이블을 읽는 질의는 생 SQL로 남는다([CongestionAreaQueryAdapter]와 같은 선례).
 *
 * 배치가 실행당 한 번만 부른다 — 252행이라 전량을 메모리에 올려 대조하는 편이 행마다 EXISTS를
 * 던지는 것보다 훨씬 싸다.
 */
@Component
class DistrictCodeQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : DistrictCodeQueryPort {

    @Transactional(readOnly = true)
    override fun findAllCodes(): Set<String> =
        jdbcTemplate.queryForList("SELECT code FROM district", String::class.java).filterNotNull().toSet()

    /** ORDER BY code — 배치가 인덱스로 진행 위치를 기억하므로 순서가 안정적이어야 한다. */
    @Transactional(readOnly = true)
    override fun findAllRegions(): List<DistrictRegion> =
        jdbcTemplate.query("SELECT code, province_code FROM district ORDER BY code") { rs, _ ->
            DistrictRegion(code = rs.getString("code"), provinceCode = rs.getString("province_code"))
        }

    /** 17행. DISTINCT는 DB에 맡긴다 — 252행을 올려 앱에서 추리는 것보다 싸다. */
    @Transactional(readOnly = true)
    override fun findAllProvinceCodes(): List<String> =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT province_code FROM district ORDER BY province_code",
            String::class.java,
        ).filterNotNull()
}
