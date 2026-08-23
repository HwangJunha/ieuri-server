package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import org.around.ieuri.common.persistence.DbSchema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 관광지 이용안내 항목 JPA 엔티티. `display_order`가 표시 순서이자 upsert 키의 일부다. */
@Entity
@Table(name = "spot_detail_info", schema = DbSchema.TOUR)
class SpotDetailInfoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "text", nullable = false)
    var text: String,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
)
