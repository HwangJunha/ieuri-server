package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.table.SpotFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.spot.provided.dto.SyncFestivalCommand
import org.around.ieuri.tourism.application.spot.provided.dto.SyncSpotCommand
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * TourAPI 원본 저장소 구현. 적재는 raw SQL upsert(`updated_at`을 값이 바뀔 때만 올리는 조건부 갱신), 정리는 DSL이다.
 * - 정리의 여집합은 메모리에서 계산한다 — `NOT IN`을 청크로 쪼개면 첫 청크가 데이터를 날린다.
 */
@Repository
class SpotPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : SpotRepository {

    /** 한 페이지를 한 번의 배치로 보내고 실제 반영된 행 수를 돌려준다. */
    override fun upsertSpots(spots: List<SyncSpotCommand>, syncedAt: Instant): Int {
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
        // 청크별 결과를 배열의 배열로 돌려준다.
        return exposedJdbcTemplate.batchUpdate(UPSERT_SPOT_SQL, spots, spots.size, setter).sumOf { chunk -> chunk.count { it != 0 } }
    }

    override fun deleteMissingSpots(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합이면 전량 삭제된다 — 게이트와 별개로 여기서도 막는다.
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = SpotTable.select(SpotTable.contentId)
            .where { SpotTable.language eq language.name }
            .map { it[SpotTable.contentId] }
            .filterNot { it in survivingContentIds }
        return deleteSpotsInChunks(language, doomed)
    }

    override fun upsertFestivals(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int {
        if (festivals.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncFestivalCommand> { ps: PreparedStatement, festival ->
            ps.setDate(1, Date.valueOf(festival.period.start))
            ps.setDate(2, Date.valueOf(festival.period.end))
            ps.setString(3, festival.progressType)
            ps.setString(4, festival.festivalType)
            ps.setTimestamp(5, syncedTimestamp)
            // 부모 spot.id는 자연키(contentId)로 SQL 안에서 해석한다.
            ps.setString(6, festival.contentId)
            ps.setString(7, festival.language.name)
        }
        return exposedJdbcTemplate.batchUpdate(UPSERT_FESTIVAL_SQL, festivals, festivals.size, setter).sumOf { chunk -> chunk.count { it != 0 } }
    }

    /** `event_end_date < asOf` — 종료일 당일은 유효하다(`FestivalPeriod.isEndedAsOf`와 같은 부등호). */
    override fun deleteEndedFestivals(language: DomainLocale, asOf: LocalDate): Int = deleteSpotsInChunks(language, findEndedFestivalContentIds(language, asOf).toList())

    /** 대상은 `spot_festival`이 아니라 축제 카테고리 `spot` 전체다 — 축제 API는 진행 중인 것만 주므로 "이번 수집에 없다 = 끝났다"다. */
    override fun deleteMissingFestivals(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합이면 그 언어의 축제가 전량 삭제된다 — 게이트와 별개로 여기서도 막는다.
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = SpotTable.select(SpotTable.contentId)
            .where { (SpotTable.language eq language.name) and (SpotTable.category eq FESTIVAL_CATEGORY) }
            .map { it[SpotTable.contentId] }
            .filterNot { it in survivingContentIds }
        return deleteSpotsInChunks(language, doomed)
    }

    /** [asOf] 기준 종료된 축제의 contentId. 삭제([deleteEndedFestivals])가 같은 조건을 쓴다. */
    override fun findEndedFestivalContentIds(language: DomainLocale, asOf: LocalDate): Set<String> = SpotFestivalTable
        .join(SpotTable, JoinType.INNER, SpotFestivalTable.spotId, SpotTable.id)
        .select(SpotTable.contentId)
        .where { (SpotTable.language eq language.name) and (SpotFestivalTable.eventEndDate less asOf) }
        .mapTo(mutableSetOf()) { it[SpotTable.contentId] }

    /**
     * [contentIds]의 `spot` 행을 [DELETE_CHUNK_SIZE]씩 나눠 지우고 총 삭제 행 수를 돌려준다.
     * - 청크는 파라미터 한도 때문의 분할이지 커밋 경계가 아니다 — 중간 실패 시 전체가 롤백된다.
     */
    private fun deleteSpotsInChunks(language: DomainLocale, contentIds: List<String>): Int {
        if (contentIds.isEmpty()) return 0
        return contentIds.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
            SpotTable.deleteWhere { (SpotTable.language eq language.name) and (SpotTable.contentId inList chunk) }
        }
    }

    private companion object {
        /** 삭제 `IN` 절 하나에 넣을 최대 id 수(드라이버 파라미터 한도). */
        const val DELETE_CHUNK_SIZE = 1_000

        /** 축제 정리 대상 카테고리 코드. */
        val FESTIVAL_CATEGORY: String = SpotCategory.FESTIVAL_EVENT.code

        /**
         * `updated_at`은 값이 바뀐 경우에만 올린다(CASE 절).
         * - `last_synced_at`은 항상 갱신한다 — 정리 단계가 "이번 실행이 이 행을 봤다"를 판정한다.
         */
        val UPSERT_SPOT_SQL = """
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

        /**
         * `updated_at`은 값이 바뀐 경우에만 올리고 `last_synced_at`은 항상 갱신한다.
         * - `SELECT ... FROM spot`으로 삽입한다 — 부모가 없으면 FK 위반 대신 0행으로 건너뛴다.
         */
        val UPSERT_FESTIVAL_SQL = """
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
