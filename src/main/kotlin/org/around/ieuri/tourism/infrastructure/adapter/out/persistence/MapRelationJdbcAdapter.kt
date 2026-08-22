package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.MapRelationPort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapRelationJpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 관계 쓰기 어댑터(SPEC-0030 확장).
 *
 * **등록만 생 SQL이다.** 멱등성이 `INSERT ... ON CONFLICT DO NOTHING`에서 나오고 JPA에는
 * 이식 가능한 upsert가 없다. 삭제는 그런 사정이 없어 파생 쿼리를 쓴다.
 *
 * 그래서 [relate]가 넣은 행은 **영속성 컨텍스트에 보이지 않는다** — 같은 트랜잭션에서
 * 관계를 맺고 다시 읽는 호출자를 만들지 말 것.
 */
@Component
class MapRelationJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val repository: MapRelationJpaRepository,
) : MapRelationPort {

    /**
     * **두 행을 한 문장에서 만든다.** 등록은 양방향이며, 한쪽만 들어가고 다른 쪽이 실패하면
     * 목록이 어긋난다 — `VALUES` 두 줄이면 그 갈라짐이 원리적으로 생기지 않는다.
     *
     * 이미 맺어져 있으면 `DO NOTHING`이라 오류가 아니다(멱등).
     */
    @Transactional
    override fun relate(memberId: Long, counterpartId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO tour.map_relation (owner_id, counterpart_id)
            VALUES (?, ?), (?, ?)
            ON CONFLICT (owner_id, counterpart_id) DO NOTHING
            """.trimIndent(),
            memberId, counterpartId,
            counterpartId, memberId,
        )
    }

    @Transactional
    override fun removeFromMyList(memberId: Long, counterpartId: Long): Boolean =
        repository.deleteByOwnerIdAndCounterpartId(memberId, counterpartId) > 0

    /** 탈퇴 정리 — 양쪽 방향을 모두 지운다(§6). */
    @Transactional
    override fun deleteAllInvolving(memberId: Long): Int =
        repository.deleteByOwnerIdOrCounterpartId(memberId, memberId)
}
