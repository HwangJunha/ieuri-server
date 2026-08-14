package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.PopularSpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.PopularSpotSearch
import org.around.ieuri.tourism.application.query.PopularSpotView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 인기 spot 조회 어댑터(SPEC-0013 R18). `spot_reaction`의 LIKE를 **카탈로그(장소) 단위로**
 * 집계하며 기간 제한이 없다. PostGIS 거리 계산 때문에 생 SQL이다.
 *
 * 반경 조건이 `spot_catalog`에 걸리므로 조인 단계에서 이미 범위가 좁혀진다. 좋아요 하한은
 * 집계 **후** HAVING으로 적용한다(AC-18.2).
 *
 * **`spot`을 읽지 않는다**(SPEC-0019). 제목·주소는 번역에서, 좌표·썸네일·분류·저작권은
 * 카탈로그에서 온다 — 그 언어의 spot 행이 없어도 번역만 있으면 순위에 오른다.
 *
 * **메인 DB가 아니라 검색 노드를 본다**(searchJdbcTemplate) — 홈 세 섹션이 같은 노드를 보게
 * 맞춘다. 좋아요는 메인에 쓰이고 논리 복제로 넘어오므로 **집계에 복제 지연이 반영된다**:
 * 방금 누른 좋아요가 인기 순위에 즉시 반영되지 않을 수 있다. 순위는 누적 총합이라 한두 건의
 * 지연이 순서를 뒤집지 않는다.
 *
 * `idx_spot_reaction_catalog_reaction (catalog_id, reaction)`이 이 집계를 받친다.
 *
 * **SPEC-0019로 집계 단위가 카탈로그(장소)가 되면서 값이 커졌다** — 같은 장소의 언어별
 * 좋아요가 합산된다. `minLikeCount` 하한의 의미가 달라지므로, 하한을 그대로 두면 통과하는
 * spot이 늘어난다. 배포 전 현재 값과 새 분포를 대조해 조정할 것.
 */
@Component
class PopularSpotQueryJdbcAdapter(
    @Qualifier("searchJdbcTemplate") private val jdbcTemplate: JdbcTemplate,
) : PopularSpotQueryPort {

    /**
     * 좋아요 내림차순 + id 오름차순. `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     *
     * 집계값이라 별칭을 WHERE에서 참조할 수 없어 서브쿼리로 한 겹 감싼다. 정렬 두 축의 방향이
     * 달라 like_count에 -1을 곱해 부호를 맞추면 seek이 행 값 비교 한 줄이 된다. 부호 반전은
     * 여기서만 하며 커서에는 원래 값이 실린다.
     */
    @Transactional(readOnly = true)
    override fun findPopular(query: PopularSpotSearch): List<PopularSpotView> {
        val (where, whereParams) = whereClause(query)
        val params = whereParams.toMutableList()
        params += query.minLikeCount // HAVING

        val seek = if (query.after != null) {
            params += -query.after.likeCount
            params += query.after.spotId
            "WHERE (-like_count, id) > (?, ?)"
        } else {
            ""
        }
        params += query.limit + 1

        return jdbcTemplate.query(
            """
            WITH c AS (SELECT $CENTER AS center)
            SELECT * FROM (
                SELECT cat.id, t.title, t.address, cat.thumbnail_image_url, cat.map_x, cat.map_y,
                       cat.category, cat.copyright,
                       ST_Distance(cat.location, c.center) AS distance_meters,
                       COUNT(r.id) AS like_count
                FROM spot_catalog cat
                -- **카탈로그 축으로 집계한다**(SPEC-0019 R-10). 같은 장소의 언어별 좋아요가
                -- 합산되어 값이 커진다 — 의도된 교정이다.
                JOIN spot_reaction r ON r.catalog_id = cat.id AND r.reaction = ?
                -- **카탈로그를 1행으로 접는 역할은 이 조인이 맡는다.** 예전에는 `s.language = ?`가
                -- 그 역할이었다 — 그 조건이 없으면 같은 카탈로그의 4개 언어 행이 각각 같은 집계를
                -- 받아 결과가 4배로 늘어났다. 이제는 `t.language = ?` + `UNIQUE(catalog_id,
                -- language)`가 카탈로그당 정확히 1행을 보장한다. 동시에 의미가 바뀌었다:
                -- "그 언어의 spot 행이 있는가"가 아니라 **"그 언어의 제목이 있는가"**다.
                JOIN spot_catalog_translation t ON t.catalog_id = cat.id AND t.language = ?
                CROSS JOIN c
                WHERE $where
                GROUP BY cat.id, t.title, t.address, cat.thumbnail_image_url,
                         cat.map_x, cat.map_y, cat.category, cat.copyright, c.center
                HAVING COUNT(r.id) >= ?
            ) ranked
            $seek
            ORDER BY like_count DESC, id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val copyright = CopyrightType.valueOf(rs.getString("copyright"))
                PopularSpotView(
                    spot = SpotView(
                        // **클라이언트에 나가는 id는 카탈로그 id다**(SPEC-0019).
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
                    ),
                    likeCount = rs.getLong("like_count"),
                )
            },
            *params.toTypedArray(),
        )
    }

    /**
     * 파라미터는 SQL의 `?` 등장 순서대로 담아 함께 반환한다. CTE의 center가 먼저이고, 그다음이
     * 두 JOIN 조건(reaction → translation의 language), 그다음이 WHERE 순서다 — HAVING의 하한은
     * 호출자가 이어 붙인다.
     */
    private fun whereClause(query: PopularSpotSearch): Pair<String, List<Any>> {
        // 카테고리 없는 카탈로그는 카드를 그릴 수 없다 — 도메인 enum에 미분류 값이 없다.
        // 좌표 없는 카탈로그는 ST_DWithin이 참을 돌려주지 않아 자연히 빠진다.
        val conditions = mutableListOf(
            "ST_DWithin(cat.location, c.center, ?)",
            // 원본이 사라진 카탈로그는 감춘다 — 상세를 열면 실패하는 장소가 되기 때문이다.
            LiveCatalogPredicate.sql("cat"),
        )
        val params = mutableListOf<Any>(
            query.center.longitude, query.center.latitude, // CTE의 center
            ReactionType.LIKE.name,                        // spot_reaction JOIN 조건
            query.language.name,                           // translation JOIN 조건
            query.radiusMeters.toDouble(),
        )

        query.category?.let {
            conditions += "cat.category = ?"
            params += it.name
        }
        return conditions.joinToString(" AND ") to params
    }

    private companion object {
        /** ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}
