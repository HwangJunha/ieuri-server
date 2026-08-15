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
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Instant
import java.time.LocalDate

/**
 * 장소 카탈로그 JPA 엔티티(SPEC-0019). 도메인 모델(SpotCatalog)과 분리되며 `mapper`가 변환한다.
 *
 * **조회가 보는 유일한 테이블이다.** `spot`은 원본으로 남고 조회 경로에서 빠진다 — 그래야
 * 그 언어의 spot 행이 없어도 번역만 있으면 목록에 나온다.
 *
 * **이름 컬럼이 없다.** 이름은 언어에 종속되므로 [SpotCatalogTranslationEntity]에만 있으며
 * KO도 그중 하나의 언어다.
 *
 * `location`(geography)은 **매핑하지 않는다** — 트리거가 map_x/map_y에서 파생시키므로
 * JPA가 PostGIS 타입을 알 필요가 없다(spot 테이블과 같은 방식).
 */
@Entity
@Table(name = "spot_catalog")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: SpotCategory,

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
 * 카탈로그의 언어별 표시값. **KO도 하나의 언어다.**
 *
 * 좌표 사본을 두는 이유는 검색(pg_search BM25)이 **평면 질의만 받기 때문이다** — JOIN이
 * 하나라도 끼면 인덱스 푸시다운이 깨져 "제목 검색 + 반경 필터"를 한 문장에 담을 수 없다.
 * 사본이므로 카탈로그 좌표가 바뀌면 함께 갱신해야 한다.
 */
@Entity
@Table(name = "spot_catalog_translation")
class SpotCatalogTranslationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 부모 카탈로그의 surrogate PK. 애그리거트 내부 참조지만 연관이 아니라 plain 컬럼이다. */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    /** 원문에 주소가 없는 spot이 실재해(KO 기준 1.5%) nullable이다. */
    @Column(name = "address", length = 500)
    var address: String? = null,

    /** 카탈로그 좌표의 사본. 검색이 이 테이블 단독으로 돌아야 해서 둔다. */
    @Column(name = "map_x", nullable = false)
    var mapX: Double,

    @Column(name = "map_y", nullable = false)
    var mapY: Double,

    /**
     * 카탈로그 분류의 사본. 좌표와 같은 이유다 — 검색이 평면 질의라 `spot_catalog`를 조인하면
     * BM25 인덱스 푸시다운이 깨져 분류 필터를 걸 자리가 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: SpotCategory,

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
 * 축제 기간(1:1, 축제인 카탈로그만).
 *
 * 기간은 **언어 무관한 장소 자체의 속성이다** — 실측상 다국어 축제 60개 중 기간이 다른 것은
 * 3개뿐이다. 원본은 `spot_festival`에 남고 이것은 KO 우선으로 확정한 사본이다.
 *
 * PK가 catalogId라 그 자체로 upsert 단위다.
 */
@Entity
@Table(name = "spot_catalog_festival")
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
