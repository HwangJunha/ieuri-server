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
 * 지도 관계 JPA 엔티티(SPEC-0030 확장). 방향이 있다 — `ownerId`의 목록에 `counterpartId`가 나타난다.
 * - 등록은 두 행을 만들고, 삭제는 자기 행만 지운다.
 * - 겹친 칸은 조회 시점에 재계산하므로 담지 않는다.
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
