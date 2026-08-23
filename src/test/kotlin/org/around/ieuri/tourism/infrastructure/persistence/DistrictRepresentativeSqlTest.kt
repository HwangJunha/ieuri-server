package org.around.ieuri.tourism.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.DistrictRepresentativeJdbcAdapter
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 대표 위치 SQL의 바인딩 인자 수 검증(SPEC-0015 · V46).
 *
 * **컴파일로 잡히지 않는 결함이다.** 인자를 하나 더 넘겨도 빌드는 통과하고, 배치를 실제로
 * 돌린 뒤에야 `column index is out of range`로 터진다 — 실제로 그렇게 터졌다.
 */
class DistrictRepresentativeSqlTest {

    private fun placeholders(sql: String): Int = sql.count { it == '?' }

    @Test
    fun `SELECT_SQL의 물음표는 두 개다 — 격자 크기와 최소 개수`() {
        assertEquals(2, placeholders(DistrictRepresentativeJdbcAdapter.SELECT_SQL))
    }

    @Test
    fun `CLEAR_SQL의 물음표는 두 개다 — 선정과 같은 기준이어야 한다`() {
        assertEquals(2, placeholders(DistrictRepresentativeJdbcAdapter.CLEAR_SQL))
    }

    @Test
    fun `어댑터가 넘기는 인자 수가 두 문장의 물음표 수와 일치한다`() {
        val jdbcTemplate = mockk<JdbcTemplate>()
        val selectArgs = slot<Array<Any>>()
        val clearArgs = slot<Array<Any>>()

        every {
            jdbcTemplate.update(DistrictRepresentativeJdbcAdapter.SELECT_SQL, *varargAll { true })
        } answers {
            @Suppress("UNCHECKED_CAST")
            selectArgs.captured = (invocation.args[1] as Array<Any>)
            226
        }
        every {
            jdbcTemplate.update(DistrictRepresentativeJdbcAdapter.CLEAR_SQL, *varargAll { true })
        } answers {
            @Suppress("UNCHECKED_CAST")
            clearArgs.captured = (invocation.args[1] as Array<Any>)
            0
        }

        val adapter = DistrictRepresentativeJdbcAdapter(jdbcTemplate)
        val result = adapter.refreshRepresentativeLocations(cellSizeDegrees = 0.02, minSpotCount = 10)

        assertEquals(
            placeholders(DistrictRepresentativeJdbcAdapter.SELECT_SQL),
            selectArgs.captured.size,
            "SELECT_SQL의 물음표 수와 인자 수가 다르다",
        )
        assertEquals(
            placeholders(DistrictRepresentativeJdbcAdapter.CLEAR_SQL),
            clearArgs.captured.size,
            "CLEAR_SQL의 물음표 수와 인자 수가 다르다",
        )
        assertEquals(226, result.selected)
        assertEquals(0, result.cleared)
    }
}
