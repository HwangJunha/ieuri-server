package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
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
            ps.setDate(1, Date.valueOf(festival.period.start))
            ps.setDate(2, Date.valueOf(festival.period.end))
            ps.setString(3, festival.progressType)
            ps.setString(4, festival.festivalType)
            ps.setTimestamp(5, syncedTimestamp)
            // 부모 조회(SELECT ... FROM spot)가 자연키를 요구한다 — 적재 입력은 여전히 TourAPI
            // contentId 기반이므로 여기서 spot.id로 해석한다.
            ps.setString(6, festival.contentId)
            ps.setString(7, festival.language.name)
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
        // [findEndedContentIds]와 같은 조건으로 대상을 뽑아 청크로 지운다. 조건이 두 곳에 있으므로
        // 한쪽만 바꾸면 R-9의 제외 집합과 실제 삭제 대상이 어긋난다 — 같은 메서드를 재사용한다.
        deleteSpotsInChunks(language, findEndedContentIds(language, asOf).toList())

    /**
     * 이번 수집 결과에 없는 축제 삭제(AC-29). **`spot_festival`에 있는 행만** 대상으로 한다 —
     * 관광지 전반을 건드리면 축제 정리가 관광지 정리를 덮어쓴다.
     *
     * [SpotJpaAdapter.deleteMissing]과 같은 이유로 **임시 테이블을 쓰지 않는다.** 삭제 대상을 먼저
     * 확정한 뒤 청크로 나눠 지운다 — 여집합은 분할 가능한 연산이 아니므로 `NOT IN`을 쪼갤 수 없고,
     * 삭제 대상 목록은 그냥 목록이라 나눌 수 있다. 축제는 절대 건수가 적어 이 조회가 가볍다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 그 언어의 축제가 전량 삭제된다. 게이트가 막아야 하지만 여기서도
        // 한 번 더 막는다 — 이 한 줄이 없으면 게이트 버그가 곧 데이터 손실이다(AC-35와 짝).
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = jdbcTemplate.queryForList(
            """
            SELECT s.content_id FROM spot_festival f
            JOIN spot s ON s.id = f.spot_id
            WHERE s.language = ?
            """.trimIndent(),
            String::class.java,
            language.name,
        ).filterNotNull().filterNot { it in survivingContentIds }

        return deleteSpotsInChunks(language, doomed)
    }

    /**
     * [contentIds]에 해당하는 **`spot` 행**을 [DELETE_CHUNK_SIZE]씩 나눠 지운다. `spot_festival`은
     * FK CASCADE로 따라 사라진다(AC-30).
     *
     * 한 트랜잭션 안에서 여러 번 `DELETE`를 치므로 중간 실패 시 전체가 롤백된다 — 청크는 파라미터
     * 한도를 넘지 않기 위한 분할이지 커밋 경계가 아니다.
     */
    private fun deleteSpotsInChunks(language: DomainLocale, contentIds: List<String>): Int {
        if (contentIds.isEmpty()) return 0
        return contentIds.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            jdbcTemplate.update(
                "DELETE FROM spot WHERE language = ? AND content_id IN ($placeholders)",
                language.name, *chunk.toTypedArray(),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String> =
        jdbcTemplate.queryForList(
            """
            SELECT s.content_id FROM spot_festival f
            JOIN spot s ON s.id = f.spot_id
            WHERE s.language = ? AND f.event_end_date < ?
            """.trimIndent(),
            String::class.java,
            language.name,
            Date.valueOf(asOf),
        ).filterNotNull().toSet()   // content_id는 NOT NULL이나 JDBC 시그니처가 nullable이다.

    private companion object {
        /**
         * 삭제 `IN` 절 하나에 넣을 최대 id 수. [SpotJpaAdapter]와 같은 값이다 — 드라이버 파라미터
         * 한도(65535)에 여유를 두기 위한 분할 단위이지 커밋 경계가 아니다.
         */
        const val DELETE_CHUNK_SIZE = 1_000

        /**
         * `updated_at`은 [SpotJpaAdapter]와 같은 이유로 **실제로 값이 바뀐 경우에만** 올린다.
         * `last_synced_at`은 반대로 항상 갱신한다.
         *
         * **`VALUES`가 아니라 `SELECT ... FROM spot`으로 삽입한다.** 두 가지를 한 번에 해결한다:
         * 부모의 surrogate id를 가져오고(V19), 부모가 없으면 0행이 삽입되어 FK 위반 예외 대신
         * 조용한 건너뜀이 된다(R-2·AC-5). 한 건의 부모 없는 축제가 페이지 전체를 롤백시킬 이유가 없다.
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
