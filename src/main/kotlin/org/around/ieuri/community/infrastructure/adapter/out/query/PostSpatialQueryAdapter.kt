package org.around.ieuri.community.infrastructure.adapter.out.query

import org.around.ieuri.community.application.port.out.query.PostsArea
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 거리순 피드의 **id 조회 전용** 어댑터. PostGIS 함수를 QueryDSL로 표현할 수 없어 이 클래스에만
 * 생 SQL이 남는다(tourism의 `SpotSpatialQueryAdapter`와 같은 관례).
 *
 * **`location`(geography) 컬럼은 엔티티에 매핑하지 않는다** — 대응하는 Kotlin 타입이 없어
 * 매핑하면 `ddl-auto=validate`가 기동을 막고, 매핑하지 않은 컬럼은 HQL이 해석하지 못해
 * QueryDSL 템플릿으로도 참조할 수 없다.
 *
 * map_x·map_y로 매번 점을 만들어 우회할 수도 있지만 **GiST 인덱스를 타지 못한다** —
 * 실측하면 Seq Scan 389ms 대 Bitmap Index Scan 119ms로 갈린다. 그래서 이 경로만 생 SQL이다.
 *
 * 모더레이션 필터(차단·숨김)는 여기서 다루지 않는다. 이 어댑터는 "반경 안에서 가까운 순으로
 * 글 id"만 돌려주고, 나머지 조건은 호출자가 QueryDSL로 건다 — 필터가 두 곳으로 갈리면
 * 한쪽만 고쳐지는 사고가 난다.
 */
@Component
class PostSpatialQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 반경 안의 글 id를 **가까운 순으로**. 같은 거리면 최신순, 그다음 id 내림차순이다 —
     * 정렬이 요청마다 흔들리면 커서 페이징이 항목을 건너뛴다.
     *
     * [afterDistanceMeters]·[afterCreatedAtEpochMilli]·[afterId]가 있으면 그 위치 다음부터
     * 읽는다(키셋). 셋 다 null이면 첫 페이지다.
     *
     * 삭제·숨김·차단 필터는 걸지 않으므로 호출자가 이 id를 자기 조건과 함께 다시 좁혀야 한다.
     * 그래서 [limit]에 여유를 두고 부르는 것이 호출자의 몫이다.
     */
    fun findPostIdsWithin(
        area: PostsArea,
        limit: Int,
        afterDistanceMeters: Double? = null,
        afterCreatedAtEpochMilli: Long? = null,
        afterId: Long? = null,
    ): List<PostDistanceRow> {
        val keyset = if (afterDistanceMeters != null) {
            """
            AND (
                ST_Distance(ps.location, $CENTER) > ?
                OR (ST_Distance(ps.location, $CENTER) = ? AND (
                    p.created_at < ? OR (p.created_at = ? AND p.id < ?)
                ))
            )
            """.trimIndent()
        } else {
            ""
        }

        // 파라미터 순서는 SQL 문자열의 ? 등장 순서다. CENTER 하나가 ?를 둘(경도·위도) 쓴다.
        val params = buildList<Any> {
            add(area.longitude); add(area.latitude)                  // SELECT의 CENTER
            add(area.longitude); add(area.latitude)                  // WHERE ST_DWithin의 CENTER
            add(area.radiusMeters.toDouble())
            if (afterDistanceMeters != null) {
                add(area.longitude); add(area.latitude)              // keyset 첫 CENTER
                add(afterDistanceMeters)
                add(area.longitude); add(area.latitude)              // keyset 둘째 CENTER
                add(afterDistanceMeters)
                val createdAt = java.sql.Timestamp(requireNotNull(afterCreatedAtEpochMilli))
                add(createdAt); add(createdAt)
                add(requireNotNull(afterId))
            }
            add(area.longitude); add(area.latitude)                  // ORDER BY의 CENTER
            add(limit)
        }

        return jdbcTemplate.query(
            """
            SELECT p.id AS post_id, ST_Distance(ps.location, $CENTER) AS distance_meters
            FROM community_post p
            JOIN community_post_spot ps ON ps.post_id = p.id
            WHERE ST_DWithin(ps.location, $CENTER, ?)
              AND p.deleted_at IS NULL
              $keyset
            ORDER BY ST_Distance(ps.location, $CENTER), p.created_at DESC, p.id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                PostDistanceRow(
                    postId = rs.getLong("post_id"),
                    distanceMeters = rs.getDouble("distance_meters"),
                )
            },
            *params.toTypedArray(),
        )
    }

    private companion object {
        /** 요청 좌표를 geography 점으로. ST_MakePoint는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다. */
        const val CENTER = "ST_MakePoint(?, ?)::geography"
    }
}

/** 거리순 조회 결과 한 행. 거리는 커서에 실려 다음 페이지의 시작점이 된다. */
data class PostDistanceRow(val postId: Long, val distanceMeters: Double)
