package org.around.ieuri.tourism.infrastructure.persistence

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.PetFriendlyUpsertJdbcAdapter
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 반려동물 upsert SQL의 바인딩 인자 수 검증(SPEC-0015 · V48).
 *
 * **컴파일로 잡히지 않는 결함이다** — setter가 세팅하는 인덱스와 SQL의 `?` 개수가 어긋나도
 * 빌드는 통과하고, 배치를 실제로 돌린 뒤에야 터진다(DistrictRepresentativeSqlTest와 같은 이유).
 */
class PetFriendlyUpsertSqlTest {

    private val sql = PetFriendlyUpsertJdbcAdapter.UPSERT_SQL

    @Test
    fun `UPSERT_SQL의 물음표는 다섯 개다 — 적재 컬럼 수와 같다`() {
        assertEquals(5, sql.count { it == '?' })
    }

    @Test
    fun `content_id 충돌에 upsert가 걸려 있다 — 재실행이 멱등이어야 한다`() {
        assertTrue(sql.contains("ON CONFLICT (content_id) DO UPDATE"))
    }

    @Test
    fun `빈 목록이면 DB를 건드리지 않는다`() {
        val jdbcTemplate = mockk<JdbcTemplate>()

        val written = PetFriendlyUpsertJdbcAdapter(jdbcTemplate).upsertAll(emptyList())

        assertEquals(0, written)
        verify(exactly = 0) { jdbcTemplate.batchUpdate(any(), any<List<SyncPetFriendlyCommand>>(), any(), any()) }
    }
}
