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
import java.time.LocalDate

/**
 * 장소 카탈로그 JPA 엔티티(SPEC-0019). 조회가 보는 테이블이며 `spot`은 원본으로 남는다.
 * - 이름은 언어에 종속되므로 [SpotCatalogTranslationEntity]에만 있다(KO 포함).
 * - `location`(geography)은 매핑하지 않는다 — 트리거가 map_x/map_y에서 파생시킨다.
 */
@Entity
@Table(name = "spot_catalog", schema = DbSchema.TOUR)
class SpotCatalogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 경도. 도메인 Coordinates.longitude에 대응. */
    @Column(name = "map_x", nullable = false)
    var mapX: Double,

    /** 위도. 도메인 Coordinates.latitude에 대응. */
    @Column(name = "map_y", nullable = false)
    var mapY: Double,

    @Column(name = "thumbnail_image_url", nullable = false, length = 500)
    var thumbnailImageUrl: String,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

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

/**
 * 카탈로그의 언어별 표시값(KO도 하나의 언어다).
 * - 좌표·분류 사본을 둔다(검색이 이 테이블 단독 평면 질의라서). 카탈로그 값이 바뀌면 함께 갱신해야 한다.
 */
@Entity
@Table(name = "spot_catalog_translation", schema = DbSchema.TOUR)
class SpotCatalogTranslationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 부모 카탈로그의 PK. 연관이 아니라 plain 컬럼이다. */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    /** 원문에 주소가 없는 spot이 있어 nullable이다. */
    @Column(name = "address", length = 500)
    var address: String? = null,

    /** 카탈로그 좌표의 사본. */
    @Column(name = "map_x", nullable = false)
    var mapX: Double,

    @Column(name = "map_y", nullable = false)
    var mapY: Double,

    /** 카탈로그 분류의 사본. */
    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    /**
     * 이 언어의 원본 `spot`이 남아 있는가. 모든 조회가 이 값으로 거른다(SPEC-0029).
     * - 행은 지우지 않는다 — 배정 배치가 이 테이블로 옛 카탈로그를 찾아 되살린다.
     */
    @Column(name = "visible", nullable = false)
    var visible: Boolean = true,

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

/**
 * 축제 기간(1:1, 축제인 카탈로그만). 언어 무관한 장소 속성이며, `spot_festival`에서 KO 우선으로 확정한 사본이다.
 * - PK가 catalogId라 그 자체로 upsert 단위다.
 */
@Entity
@Table(name = "spot_catalog_festival", schema = DbSchema.TOUR)
class SpotCatalogFestivalEntity(
    @Id
    @Column(name = "catalog_id")
    var catalogId: Long,

    @Column(name = "event_start_date", nullable = false)
    var eventStartDate: LocalDate,

    @Column(name = "event_end_date", nullable = false)
    var eventEndDate: LocalDate,

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
