package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.application.port.out.persistence.FestivalUpsertPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

/**
 * 축제 정보 upsert 전용 어댑터. [SpotUpsertJdbcAdapter]와 같은 이유로 이 어댑터에만 생 SQL이 남는다.
 */
@Component
class FestivalUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : FestivalUpsertPort {

    /** [festivals]를 한 번의 배치로 upsert하고 실제 반영된 행 수를 돌려준다. */
    @Transactional
    override fun upsertAll(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int {
        if (festivals.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncFestivalCommand> { ps: PreparedStatement, festival ->
            ps.setDate(1, Date.valueOf(festival.period.start))
            ps.setDate(2, Date.valueOf(festival.period.end))
            ps.setString(3, festival.progressType)
            ps.setString(4, festival.festivalType)
            ps.setTimestamp(5, syncedTimestamp)
            // 부모 조회가 자연키를 요구한다 — 적재 입력은 TourAPI contentId 기반이므로 여기서 spot.id로 해석한다.
            ps.setString(6, festival.contentId)
            ps.setString(7, festival.language.name)
        }
        return jdbcTemplate.batchUpdate(UPSERT_SQL, festivals, festivals.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    private companion object {
        /**
         * `updated_at`은 실제로 값이 바뀐 경우에만 올리고 `last_synced_at`은 항상 갱신한다.
         *
         * `VALUES`가 아니라 `SELECT ... FROM spot`으로 삽입해 부모의 surrogate id를 가져오고,
         * 부모가 없으면 0행이 삽입되어 FK 위반 예외 대신 조용한 건너뜀이 된다 — 한 건의 부모 없는
         * 축제가 페이지 전체를 롤백시킬 이유가 없다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot_festival (
                spot_id, event_start_date, event_end_date,
                progress_type, festival_type, last_synced_at
            )
            SELECT s.id, ?, ?, ?, ?, ?
            FROM spot s WHERE s.content_id = ? AND s.language = ?
            ON CONFLICT (spot_id) DO UPDATE SET
                event_start_date = EXCLUDED.event_start_date,
                event_end_date   = EXCLUDED.event_end_date,
                progress_type    = EXCLUDED.progress_type,
                festival_type    = EXCLUDED.festival_type,
                last_synced_at   = EXCLUDED.last_synced_at,
                updated_at       = CASE
                    WHEN spot_festival.event_start_date IS DISTINCT FROM EXCLUDED.event_start_date
                      OR spot_festival.event_end_date   IS DISTINCT FROM EXCLUDED.event_end_date
                      OR spot_festival.progress_type    IS DISTINCT FROM EXCLUDED.progress_type
                      OR spot_festival.festival_type    IS DISTINCT FROM EXCLUDED.festival_type
                    THEN now()
                    ELSE spot_festival.updated_at
                END
        """.trimIndent()
    }
}
