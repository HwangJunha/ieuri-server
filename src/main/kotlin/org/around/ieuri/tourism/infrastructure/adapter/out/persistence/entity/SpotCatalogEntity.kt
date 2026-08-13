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
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import java.time.Instant

/**
 * 카탈로그 JPA 엔티티(SPEC-0019). 도메인 모델(SpotCatalog)과 분리되며 `mapper` 패키지가 변환한다.
 *
 * 번역은 `@OneToMany`로 걸지 않는다 — 컬렉션을 건드리는 순간 쿼리가 나가고 크기를 제한할 수
 * 없다. 자식이 `catalog_id`를 들고 있고, 읽기는 QueryDSL 프로젝션이 담당한다.
 */
@Entity
@Table(name = "spot_catalog")
class SpotCatalogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "representative_name_ko", nullable = false, length = 255)
    var representativeNameKo: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", nullable = false, length = 20)
    var nameSource: CatalogNameSource,

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
 * 카탈로그의 언어별 제목. `UNIQUE(catalog_id, language)`가 "한 카탈로그는 언어당 제목 하나"를
 * 강제한다.
 *
 * [source]가 없으면 TourAPI가 나중에 공식 번역을 제공했을 때 우리 번역을 덮어써도 되는지
 * 판단할 수 없다(R-5).
 */
@Entity
@Table(name = "spot_catalog_translation")
class SpotCatalogTranslationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 부모 카탈로그의 surrogate PK. 애그리거트 내부 참조지만 연관이 아니라 plain 컬럼으로 둔다. */
    @Column(name = "catalog_id", nullable = false)
    var catalogId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    var source: TranslationSource,

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
