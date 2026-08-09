package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.FestivalQueryPort
import org.around.ieuri.tourism.application.port.out.query.OngoingFestivalSearch
import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date

/**
 * 진행 중 축제 조회 어댑터(SPEC-0013 R17). `spot_festival`의 최초 읽기 경로다 — 지금까지 이
 * 테이블은 배치 쓰기와 정리 삭제에만 쓰였다.
 *
 * PostGIS 거리 계산·정렬 때문에 생 SQL이다. `spot_festival`에는 language 컬럼이 없어 `spot`을
 * 조인해 언어를 건다(축제 행은 spot_id로만 언어가 결정된다).
 *
 * **메인 DB가 아니라 검색 노드를 본다**(searchJdbcTemplate) — 홈 화면 세 섹션이 한 노드를
 * 보게 해서, 검색과 같은 복제 지연을 공유하도록 맞춘다. 섹션마다 데이터 신선도가 다르면
 * 같은 spot이 섹션에 따라 있고 없고 하는 상태가 된다.
 *
 * **날짜 필터를 조회 시점에 적용한다** — 배치의 종료 축제 정리에 기대지 않는다. 배치가 실패해
 * 종료된 축제가 남아 있어도 결과는 R17을 지킨다.
 */
@Component
class FestivalQueryJdbcAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : FestivalQueryPort {

    /**
     * 거리 오름차순 + id 오름차순. `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     * 거리가 계산식이라 별칭을 WHERE에서 참조할 수 없어 서브쿼리로 한 겹 감싼다.
     * 두 정렬 축이 모두 오름차순이라 seek은 행 값 비교 한 줄이다.
     */
    @Transactional(readOnly = true)
    override fun findOngoing(query: OngoingFestivalSearch): List<FestivalView> {
        val (where, whereParams) = whereClause(query)
        val params = whereParams.toMutableList()

        val seek = if (query.after != null) {
            params += query.after.distanceMeters
            params += query.after.spotId
            "WHERE (distance_meters, id) > (?, ?)"
        } else {
            ""
        }
        params += query.limit + 1

        return jdbcTemplate.query(
            """
            WITH c AS (SELECT $CENTER AS center)
            SELECT * FROM (
                SELECT s.id, s.title, s.address, s.thumbnail_image_url, s.map_x, s.map_y,
                       s.category, s.copyright,
                       f.event_start_date, f.event_end_date,
                       ST_Distance(s.location, c.center) AS distance_meters
                FROM spot_festival f
                JOIN spot s ON s.id = f.spot_id
                CROSS JOIN c
                WHERE $where
            ) ranked
            $seek
            ORDER BY distance_meters ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val copyright = CopyrightType.valueOf(rs.getString("copyright"))
                FestivalView(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    address = rs.getString("address"),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    distanceMeters = rs.getDouble("distance_meters"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    copyright = copyright,
                    attributionText = copyright.attributionText,
                    mapX = rs.getDouble("map_x"),
                    mapY = rs.getDouble("map_y"),
                    eventStartDate = rs.getDate("event_start_date").toLocalDate(),
                    eventEndDate = rs.getDate("event_end_date").toLocalDate(),
                )
            },
            *params.toTypedArray(),
        )
    }

    /**
     * 파라미터는 SQL의 `?` 등장 순서대로 담아 함께 반환한다. CTE의 center가 항상 앞선다.
     *
     * 기간 판정은 **경계를 포함한다**(AC-17.3) — 오늘이 시작일 또는 종료일과 같아도 진행 중이다.
     * '오늘'은 서비스가 시계에서 읽어 넘긴 값이며 `CURRENT_DATE`를 쓰지 않는다.
     */
    private fun whereClause(query: OngoingFestivalSearch): Pair<String, List<Any>> {
        val conditions = listOf(
            "s.language = ?",
            "f.event_start_date <= ?",
            "f.event_end_date >= ?",
            "ST_DWithin(s.location, c.center, ?)",
        )
        val params = listOf<Any>(
            query.center.longitude, query.center.latitude, // CTE의 center
            query.language.name,
            Date.valueOf(query.asOf),
            Date.valueOf(query.asOf),
            query.radiusMeters.toDouble(),
        )
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
