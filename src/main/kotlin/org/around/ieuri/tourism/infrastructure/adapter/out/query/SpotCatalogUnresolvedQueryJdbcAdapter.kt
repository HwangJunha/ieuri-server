package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.UnresolvedCandidate
import org.around.ieuri.tourism.application.port.out.query.UnresolvedSpotReport
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 미해결 건 조회 어댑터(SPEC-0019 R-8). `candidate_spot_ids`가 `BIGINT[]`이고 **JPA가 배열
 * 컬럼을 표현하지 못해** 이 조회만 생 SQL이다 — 엔티티에 매핑하면 `ddl-auto: validate`가
 * 부팅 시 실패한다.
 *
 * 후보의 좌표·카테고리는 배열을 펼쳐 `spot`에 조인해 가져온다. 사람이 판정하려면
 * "무엇과 헷갈렸는가"를 알아야 하기 때문이다.
 */
@Component
class SpotCatalogUnresolvedQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 미해결 전량. 실측 509건(0.81%)이라 페이징하지 않는다 — 한 번에 CSV로 쓴다.
     *
     * 이 수가 크게 늘면 정규화나 상류 데이터가 바뀐 것이며, 그 자체가 봐야 할 신호다.
     */
    @Transactional(readOnly = true)
    fun findUnresolved(): List<UnresolvedSpotReport> {
        val candidatesBySpot = loadCandidates()

        return jdbcTemplate.query(
            """
            SELECT u.spot_id, u.reason, u.extracted_key,
                   s.language, s.title, s.category, s.map_x, s.map_y, s.thumbnail_image_url
            FROM spot_catalog_unresolved u
            JOIN spot s ON s.id = u.spot_id
            ORDER BY u.reason, u.spot_id
            """.trimIndent(),
        ) { rs, _ ->
            val spotId = rs.getLong("spot_id")
            UnresolvedSpotReport(
                spotId = spotId,
                language = DomainLocale.valueOf(rs.getString("language")),
                title = rs.getString("title"),
                category = SpotCategory.valueOf(rs.getString("category")),
                mapX = rs.getDouble("map_x"),
                mapY = rs.getDouble("map_y"),
                reason = rs.getString("reason"),
                extractedKey = rs.getString("extracted_key"),
                thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                candidates = candidatesBySpot[spotId].orEmpty(),
            )
        }
    }

    /**
     * 후보를 spot별로 미리 모아 둔다. 행마다 따로 조회하면 509번 왕복한다.
     *
     * `unnest`로 배열을 펼쳐 한 번에 조인한다.
     */
    private fun loadCandidates(): Map<Long, List<UnresolvedCandidate>> =
        jdbcTemplate.query(
            """
            SELECT u.spot_id AS owner_id,
                   c.id, c.title, c.category, c.map_x, c.map_y
            FROM spot_catalog_unresolved u
            CROSS JOIN LATERAL unnest(u.candidate_spot_ids) AS candidate_id
            JOIN spot c ON c.id = candidate_id
            ORDER BY u.spot_id, c.id
            """.trimIndent(),
        ) { rs, _ ->
            rs.getLong("owner_id") to UnresolvedCandidate(
                spotId = rs.getLong("id"),
                title = rs.getString("title"),
                category = SpotCategory.valueOf(rs.getString("category")),
                mapX = rs.getDouble("map_x"),
                mapY = rs.getDouble("map_y"),
            )
        }
            .groupBy({ it.first }) { it.second }
}
