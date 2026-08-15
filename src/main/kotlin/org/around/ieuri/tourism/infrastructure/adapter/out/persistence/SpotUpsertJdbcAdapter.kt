package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

/**
 * 관광지 목록 upsert 전용 어댑터. JPA에 이식 가능한 upsert가 없어 이 어댑터에만 생 SQL이 남는다.
 *
 * 한 페이지를 한 번의 `executeBatch`로 보내 왕복 횟수를 줄인다. 충돌 시 전체 컬럼을 무조건
 * 덮어쓴다 — 해시 비교로 변경을 감지하면 낡은 저작권 분류가 무기한 남는다.
 */
@Component
class SpotUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /** [spots]를 한 번의 배치로 upsert하고 실제 반영된 행 수를 돌려준다. */
    @Transactional
    fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int {
        if (spots.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncSpotCommand> { ps: PreparedStatement, spot ->
            ps.setString(1, spot.contentId)
            ps.setString(2, spot.language.name)
            ps.setString(3, spot.title)
            ps.setString(4, spot.address)
            ps.setString(5, spot.thumbnailImageUrl)
            ps.setDouble(6, spot.coordinates.longitude)
            ps.setDouble(7, spot.coordinates.latitude)
            ps.setString(8, spot.category.code)
            ps.setInt(9, spot.contentTypeId)
            ps.setString(10, spot.copyright.name)
            ps.setTimestamp(11, syncedTimestamp)
        }
        // batchUpdate(sql, batchArgs, batchSize, pss)는 청크별 결과를 배열의 배열로 돌려준다.
        return jdbcTemplate.batchUpdate(UPSERT_SQL, spots, spots.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    private companion object {
        /**
         * `updated_at`은 실제로 값이 바뀐 경우에만 올린다(CASE 절). 무조건 갱신하면 매일 전 행이
         * 변경된 것처럼 보인다. `last_synced_at`은 반대로 항상 갱신해야 정리 단계가
         * "이번 실행이 이 행을 봤다"를 판정할 수 있다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot (
                content_id, language, title, address, thumbnail_image_url,
                map_x, map_y, category, content_type_id, copyright, last_synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (content_id, language) DO UPDATE SET
                title               = EXCLUDED.title,
                address             = EXCLUDED.address,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url,
                map_x               = EXCLUDED.map_x,
                map_y               = EXCLUDED.map_y,
                category            = EXCLUDED.category,
                content_type_id     = EXCLUDED.content_type_id,
                copyright           = EXCLUDED.copyright,
                last_synced_at      = EXCLUDED.last_synced_at,
                updated_at          = CASE
                    WHEN spot.title               IS DISTINCT FROM EXCLUDED.title
                      OR spot.address             IS DISTINCT FROM EXCLUDED.address
                      OR spot.thumbnail_image_url IS DISTINCT FROM EXCLUDED.thumbnail_image_url
                      OR spot.map_x               IS DISTINCT FROM EXCLUDED.map_x
                      OR spot.map_y               IS DISTINCT FROM EXCLUDED.map_y
                      OR spot.category            IS DISTINCT FROM EXCLUDED.category
                      OR spot.content_type_id     IS DISTINCT FROM EXCLUDED.content_type_id
                      OR spot.copyright           IS DISTINCT FROM EXCLUDED.copyright
                    THEN now()
                    ELSE spot.updated_at
                END
        """.trimIndent()
    }
}
