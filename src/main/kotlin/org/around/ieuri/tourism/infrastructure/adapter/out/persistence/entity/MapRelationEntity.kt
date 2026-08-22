package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant

/**
 * 지도 관계 JPA 엔티티(SPEC-0030 확장).
 *
 * **방향이 있다.** `ownerId`가 이 행을 자기 목록에서 보는 사람이고 `counterpartId`가 거기
 * 나타나는 상대다. 등록 한 번에 두 행이 생기며, 삭제는 자기 행만 지운다 — 상대 목록은
 * 건드리지 않는다.
 *
 * **겹친 칸을 담지 않는다.** 조회 시점에 재계산하는 값이라 저장하면 서로 여행을 더 다녀도
 * 숫자가 그대로가 된다.
 */
@Entity
@Table(name = "map_relation", schema = DbSchema.TOUR)
class MapRelationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "owner_id", nullable = false)
    var ownerId: Long,

    @Column(name = "counterpart_id", nullable = false)
    var counterpartId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
