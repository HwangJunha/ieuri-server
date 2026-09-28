package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant

/**
 * 관광지 목록 JPA 엔티티. PK는 surrogate id, upsert 단위는 자연키 (content_id, language) UNIQUE(uq_spot).
 * - 도메인 `Spot`의 `distance`(조회 시 계산)·`attributionText`(copyright에서 파생)는 컬럼이 없다.
 */
@Entity
@Table(name = "spot", schema = DbSchema.TOUR)
class SpotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "content_id", nullable = false, length = 32)
    var contentId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    @Column(name = "address", length = 500)
    var address: String? = null,

    @Column(name = "thumbnail_image_url", nullable = false, length = 500)
    var thumbnailImageUrl: String,

    /** 경도. 도메인 Coordinates.longitude에 대응. */
    @Column(name = "map_x", nullable = false)
    var mapX: Double,

    /** 위도. 도메인 Coordinates.latitude에 대응. */
    @Column(name = "map_y", nullable = false)
    var mapY: Double,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    /** 해석 이전의 TourAPI 원본 contentTypeId. 적재 전용 — 응답·로그로 내보내지 않는다(SPEC-0002 R8·AC-21). */
    @Column(name = "content_type_id")
    var contentTypeId: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

    /** 이번 동기화가 이 행을 건드린 시각. 사라진 항목 정리(R-5)의 기준. */
    @Column(name = "last_synced_at", nullable = false)
    var lastSyncedAt: Instant = Instant.now(),

    /** 소속 카탈로그(SPEC-0019). 배정 배치가 채우므로 새로 적재된 행은 다음 배정까지 null이다. */
    @Column(name = "catalog_id")
    var catalogId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
