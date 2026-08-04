package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.around.ieuri.tourism.domain.vo.CopyrightType
import java.time.Instant

/** 관광지 갤러리 이미지 JPA 엔티티. `display_order`가 표시 순서이자 upsert 키의 일부다. */
@Entity
@Table(name = "spot_detail_image")
class SpotDetailImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

    @Column(name = "image_url", nullable = false, length = 500)
    var imageUrl: String,

    @Column(name = "thumbnail_url", length = 500)
    var thumbnailUrl: String? = null,

    @Column(name = "caption", length = 500)
    var caption: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
)
