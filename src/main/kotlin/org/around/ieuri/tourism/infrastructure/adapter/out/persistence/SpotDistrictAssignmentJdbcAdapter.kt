package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotDistrictAssignmentPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 카탈로그 시군구 귀속 어댑터(SPEC-0030 R11 · V75). geography 컬럼을 다뤄 생 SQL이다. */
@Component
class SpotDistrictAssignmentJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotDistrictAssignmentPort {

    @Transactional
    override fun assignUnassignedDistricts(): Int = jdbcTemplate.update(ASSIGN_SQL)

    @Transactional(readOnly = true)
    override fun countUnassigned(): Int = jdbcTemplate.queryForObject(COUNT_UNASSIGNED_SQL, Int::class.java) ?: 0

    private companion object {
        /**
         * 카탈로그 좌표를 품은 시군구를 채운다.
         * - 컬럼을 `::geometry`로 캐스팅하지 않는다 — GiST 인덱스가 죽는다.
         * - 바다·경계 밖 좌표는 매칭되지 않아 매번 다시 시도된다.
         * - 경계에 걸친 좌표는 `UPDATE ... FROM`이 인접 시군구 중 임의의 한 행을 쓴다.
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
