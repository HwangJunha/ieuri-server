package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotDistrictAssignmentPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 시군구 귀속 어댑터(SPEC-0030 R11 · V75).
 *
 * `district.boundary`와 `spot_catalog.location`이 `geography`라 JPA에 매핑되어 있지 않고,
 * 그래서 이 질의는 생 SQL로 남는다([DistrictRepresentativeJdbcAdapter]와 같은 선례).
 */
@Component
class SpotDistrictAssignmentJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotDistrictAssignmentPort {

    @Transactional
    override fun assignUnassignedDistricts(): Int = jdbcTemplate.update(ASSIGN_SQL)

    @Transactional(readOnly = true)
    override fun countUnassigned(): Int =
        jdbcTemplate.queryForObject(COUNT_UNASSIGNED_SQL, Int::class.java) ?: 0

    private companion object {
        /**
         * **컬럼을 캐스팅하지 않는다.** 양쪽 다 geography이고 `idx_district_boundary`가
         * geography GiST라 그대로 인덱스를 탄다 — 어느 한쪽을 `::geometry`로 바꾸면 인덱스가
         * 죽는다(실측된 함정: 291,650 → 1,112).
         *
         * `district_code IS NULL`이 대상을 좁히지만, **바다·경계 밖 좌표는 매번 다시 시도된다** —
         * 영원히 매칭되지 않는 273건이라 그렇다. 전량이 1.7초라 재시도 비용이 그것을 구분할
         * 컬럼 하나보다 싸다는 판단이다.
         *
         * 한 시군구에만 걸리는 것이 정상이지만 경계에 걸친 좌표가 두 행을 만들 수 있다.
         * PostgreSQL의 `UPDATE ... FROM`은 그 경우 임의의 한 행을 쓴다 — 어느 쪽이든 인접한
         * 시군구이므로 결과가 틀리지 않는다.
         */
        const val ASSIGN_SQL = """
            UPDATE tour.spot_catalog cat
            SET district_code = d.code,
                updated_at    = now()
            FROM tour.district d
            WHERE cat.district_code IS NULL
              AND ST_Intersects(d.boundary, cat.location)
        """

        const val COUNT_UNASSIGNED_SQL = """
            SELECT count(*) FROM tour.spot_catalog WHERE district_code IS NULL
        """
    }
}
