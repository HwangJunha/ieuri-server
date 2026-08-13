package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.ReactionDedupePort
import org.around.ieuri.tourism.domain.specification.ReactionRow
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요 dedupe 어댑터(SPEC-0019 §4.2).
 *
 * 충돌 묶음 조회만 생 SQL이다 — **`COUNT(*) OVER (PARTITION BY ...)` 윈도 함수를 QueryDSL이
 * 표현하지 못한다.** "중복인 묶음만"을 서브쿼리 `IN`으로 쓰면 같은 테이블을 두 번 훑는다.
 *
 * 삭제는 QueryDSL이다 — 판정이 이미 끝난 id 목록을 지우는 것뿐이다.
 */
@Component
class ReactionDedupeJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val queryFactory: JPAQueryFactory,
) : ReactionDedupePort {

    private val reaction = QSpotReactionEntity.spotReactionEntity

    /**
     * `(member_id, catalog_id)`가 둘 이상인 묶음의 행 전부.
     *
     * **묶음 단위로 잘라야 한다** — 행 수로 자르면 한 묶음이 회차 경계에 걸려 일부만 보이고,
     * 그 회차의 판정이 "이 묶음에서 최신"을 잘못 고른다. `dense_rank`로 묶음에 번호를 매겨
     * 자른다.
     */
    @Transactional(readOnly = true)
    override fun findConflicting(limit: Int): List<ReactionRow> =
        jdbcTemplate.query(
            """
            SELECT id, member_id, catalog_id, updated_at
            FROM (
                SELECT id, member_id, catalog_id, updated_at,
                       COUNT(*)     OVER (PARTITION BY member_id, catalog_id) AS group_size,
                       DENSE_RANK() OVER (ORDER BY member_id, catalog_id)     AS group_no
                FROM spot_reaction
                WHERE catalog_id IS NOT NULL
            ) ranked
            WHERE group_size > 1 AND group_no <= ?
            """.trimIndent(),
            { rs, _ ->
                ReactionRow(
                    id = rs.getLong("id"),
                    memberId = rs.getLong("member_id"),
                    catalogId = rs.getLong("catalog_id"),
                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                )
            },
            limit,
        )

    /**
     * 판정에서 밀린 행을 지운다. 벌크 삭제라 영속성 컨텍스트를 우회한다 — 이 트랜잭션에서
     * 같은 엔티티를 다시 읽는 코드가 없으므로 안전하다.
     */
    @Transactional
    override fun deleteByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        return ids.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
            queryFactory.delete(reaction).where(reaction.id.`in`(chunk)).execute().toInt()
        }
    }

    private companion object {
        /** 삭제 `IN` 절 하나에 넣을 최대 id 수. 드라이버 파라미터 한도를 넘지 않기 위한 분할이다. */
        const val DELETE_CHUNK_SIZE = 1_000
    }
}
