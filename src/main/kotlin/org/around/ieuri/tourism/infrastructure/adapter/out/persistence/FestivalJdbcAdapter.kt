package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * 축제 정보 적재 포트 구현(SPEC-0007). [SpotJpaAdapter]와 같은 규율이다 — 네이티브
 * `INSERT ... ON CONFLICT` + JDBC 배치로 upsert하고, Command ↔ 테이블 매핑을 여기서 담당한다
 * (별도 Mapper 클래스 없음, 프로젝트 관례).
 *
 * 삭제가 전부 **`spot`을 대상으로** 한다는 점이 이 어댑터의 핵심이다. `spot_festival`을 직접 지우는
 * SQL은 하나도 없다 — 자식 행은 FK CASCADE로 따라간다(R-8·AC-30).
 */
@Component
class FestivalJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : FestivalStorePort {

    @Transactional
    override fun upsertAll(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int {
        if (festivals.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncFestivalCommand> { ps: PreparedStatement, festival ->
            ps.setString(1, festival.contentId)
            ps.setString(2, festival.language.name)
            ps.setDate(3, Date.valueOf(festival.period.start))
            ps.setDate(4, Date.valueOf(festival.period.end))
            ps.setString(5, festival.progressType)
            ps.setString(6, festival.festivalType)
            ps.setTimestamp(7, syncedTimestamp)
            // 부모 존재 확인(EXISTS) 절이 같은 키를 한 번 더 요구한다 — 아래 UPSERT_SQL 주석 참조.
            ps.setString(8, festival.contentId)
            ps.setString(9, festival.language.name)
        }
        return jdbcTemplate.batchUpdate(UPSERT_SQL, festivals, festivals.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    /**
     * 종료된 축제 삭제(R-8). **`event_end_date < asOf`** — 종료일 당일은 아직 유효하다(AC-27).
     * 도메인 `FestivalPeriod.isEndedAsOf`와 같은 부등호여야 한다.
     */
    @Transactional
    override fun deleteEnded(language: DomainLocale, asOf: LocalDate): Int =
        jdbcTemplate.update(
            """
            DELETE FROM spot s
            USING spot_festival f
            WHERE f.content_id = s.content_id
              AND f.language = s.language
              AND s.language = ?
              AND f.event_end_date < ?
            """.trimIndent(),
            language.name,
            Date.valueOf(asOf),
        )

    /**
     * 이번 수집 결과에 없는 축제 삭제(AC-29). [SpotJpaAdapter.deleteMissing]과 같은 임시 테이블
     * anti-join 방식이되, **`spot_festival`에 있는 행만** 대상으로 한다 — 관광지 전반을 건드리면
     * 축제 정리가 관광지 정리를 덮어쓴다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 그 언어의 축제가 전량 삭제된다. 게이트가 막아야 하지만 여기서도
        // 한 번 더 막는다 — 이 한 줄이 없으면 게이트 버그가 곧 데이터 손실이다(AC-35와 짝).
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        jdbcTemplate.execute(
            "CREATE TEMP TABLE surviving_festival (content_id VARCHAR(32) PRIMARY KEY) ON COMMIT DROP",
        )
        val ids = survivingContentIds.toList()
        jdbcTemplate.batchUpdate(
            "INSERT INTO surviving_festival (content_id) VALUES (?) ON CONFLICT DO NOTHING",
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = ids.size
                override fun setValues(ps: PreparedStatement, i: Int) = ps.setString(1, ids[i])
            },
        )
        return jdbcTemplate.update(
            """
            DELETE FROM spot s
            USING spot_festival f
            WHERE f.content_id = s.content_id
              AND f.language = s.language
              AND s.language = ?
              AND NOT EXISTS (SELECT 1 FROM surviving_festival t WHERE t.content_id = f.content_id)
            """.trimIndent(),
            language.name,
        )
    }

    @Transactional(readOnly = true)
    override fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String> =
        jdbcTemplate.queryForList(
            """
            SELECT content_id FROM spot_festival
            WHERE language = ? AND event_end_date < ?
            """.trimIndent(),
            String::class.java,
            language.name,
            Date.valueOf(asOf),
        ).filterNotNull().toSet()   // content_id는 NOT NULL이나 JDBC 시그니처가 nullable이다.

    private companion object {
        /**
         * `updated_at`은 [SpotJpaAdapter]와 같은 이유로 **실제로 값이 바뀐 경우에만** 올린다.
         * `last_synced_at`은 반대로 항상 갱신한다.
         *
         * **`ON CONFLICT (content_id, language) WHERE ...`가 아니라 부모 존재 조건을 `SELECT`로
         * 건다.** `INSERT ... SELECT ... WHERE EXISTS`는 부모 `spot` 행이 없으면 0행을 삽입하므로,
         * FK 위반 예외 대신 조용한 건너뜀이 된다(R-2·AC-5). 한 건의 부모 없는 축제가 페이지 전체를
         * 롤백시킬 이유가 없다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot_festival (
                content_id, language, event_start_date, event_end_date,
                progress_type, festival_type, last_synced_at
            )
            SELECT ?, ?, ?, ?, ?, ?, ?
            WHERE EXISTS (
                SELECT 1 FROM spot s WHERE s.content_id = ? AND s.language = ?
            )
            ON CONFLICT (content_id, language) DO UPDATE SET
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
