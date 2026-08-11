package org.around.ieuri.tourism.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.DistrictRepresentativeJdbcAdapter
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // ── 카테고리 구성비 집계(V50) ────────────────────────────────────

    @Test
    fun `CATEGORY_MIX_SQL은 바인딩 인자가 없다 — 인자를 넘기면 실행 시점에 터진다`() {
        assertEquals(0, placeholders(DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL))
    }

    @Test
    fun `집계 SQL은 KO spot만 센다 — 전 언어를 세면 번역 많은 지역이 부풀려진다`() {
        assertTrue(
            DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL.contains("sp.language = 'KO'"),
            "언어 필터가 빠지면 같은 장소가 최대 4번 잡힌다",
        )
    }

    @Test
    fun `집계 SQL은 LEFT JOIN이다 — spot 없는 시군구도 갱신되어 낡은 값이 남지 않는다`() {
        assertTrue(
            DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL.contains("LEFT JOIN counted"),
            "안쪽 조인이면 spot 0건 시군구가 UPDATE 대상에서 빠진다",
        )
    }

    @Test
    fun `집계 SQL은 KO에서 나올 수 있는 카테고리를 모두 센다`() {
        // TRANSPORT는 외국어 전용이라 KO 집계에 나타나지 않는다 — 컬럼도 없다.
        val expected = SpotCategory.entries.filterNot { it.foreignOnly }
        val sql = DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL

        val missing = expected.filterNot { sql.contains("sp.category = '${it.name}'") }
        assertEquals(
            emptyList(),
            missing,
            "집계에서 빠진 카테고리는 total에만 잡히고 어디에도 분류되지 않는다",
        )
    }

    @Test
    fun `집계 SQL은 0이 아니라 NULL을 남긴다 — COALESCE로 0을 채우지 않는다`() {
        // POL-043: 없는 데이터와 0건은 다르다. LEFT JOIN의 NULL이 그대로 컬럼에 들어가야 한다.
        assertTrue(
            !DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL.contains("COALESCE", ignoreCase = true),
            "COALESCE(...,0)을 쓰면 미집계와 0건이 구분되지 않는다",
        )
    }

    @Test
    fun `어댑터가 집계 SQL을 인자 없이 실행한다`() {
        val jdbcTemplate = mockk<JdbcTemplate>()
        every { jdbcTemplate.update(DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL) } returns 252

        val updated = DistrictRepresentativeJdbcAdapter(jdbcTemplate).refreshCategoryMix()

        assertEquals(252, updated)
        verify(exactly = 1) { jdbcTemplate.update(DistrictRepresentativeJdbcAdapter.CATEGORY_MIX_SQL) }
    }
}
