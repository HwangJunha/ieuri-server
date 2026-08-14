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
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.TranslationSource
import java.time.Instant
import java.time.LocalDate

/**
 * 장소 카탈로그 JPA 엔티티(SPEC-0019). 도메인 모델(SpotCatalog)과 분리되며 `mapper`가 변환한다.
 *
 * **조회가 보는 유일한 테이블이다.** `spot`은 원본으로 남고 조회 경로에서 빠진다 — 그래야
 * 그 언어의 spot 행이 없어도 번역만 있으면 목록에 나온다.
 *
 * 언어 무관 값(좌표·썸네일·분류)을 여기 확정해 둔다. 매 조회마다 `spot`에서 고르면 조회 곳마다
 * 규칙이 갈리고, 같은 카탈로그가 조회마다 다른 썸네일을 돌려준다.
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

    @Column(name = "representative_name_ko", nullable = false, length = 255)
    var representativeNameKo: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", nullable = false, length = 20)
    var nameSource: CatalogNameSource,

    /**
     * 매칭 조회 키. 비한국어 행이 유입되면 제목 괄호의 한글을 이 값과 대조해 카탈로그를 찾는다.
     *
     * 정규화 규칙은 도메인([org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer])이
     * 소유한다. 정규화 결과가 빈 문자열일 수 있어 nullable이다.
     */
    @Column(name = "name_key", length = 255)
    var nameKey: String? = null,

    /**
     * 확정 좌표. **유효 좌표만 담는다** — 원본에 (0,0)과 한국 밖 값이 317건 있다.
     * 배치가 유효한 것을 KO → EN → JA → ZH 순으로 고른다.
     *
     * 전부 무효면 null이며 그 카탈로그는 지도·반경 조회에서 빠진다.
     */
    @Column(name = "map_x")
    var mapX: Double? = null,

    @Column(name = "map_y")
    var mapY: Double? = null,

    @Column(name = "thumbnail_image_url", length = 500)
    var thumbnailImageUrl: String? = null,

    /** KO 우선으로 확정한 분류. 외국어는 TOURIST_SPOT으로 뭉개는 경향이 있다(실측 88건 중 65%). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    var category: SpotCategory? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", length = 20)
    var copyright: CopyrightType? = null,

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
 * 카탈로그의 언어별 표시값 — 제목과 주소.
 *
 * **주소가 함께 있어야 한다**(R-4). 제목만 번역하면 일본어 제목 아래 한국어 주소가 붙어
 * 그 장소를 찾아갈 수 없다.
 *
 * `UNIQUE(catalog_id, language)`가 "한 카탈로그는 언어당 값 하나"를 강제하며, 조회가 그 제약에
 * 기대 1행을 보장한다.
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

    /**
     * `OFFICIAL`(TourAPI 발급) / `MACHINE`(우리 번역).
     *
     * **이 컬럼이 없으면 TourAPI가 나중에 공식 번역을 제공했을 때 덮어써도 되는지 판단할 수
     * 없다**(R-5).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    var source: TranslationSource,

    /**
     * 카탈로그 좌표의 사본(V55). **검색이 이 테이블 단독으로 돌아야 해서 둔다** — BM25 인덱스가
     * 여기 걸리는데 pg_search가 평면 질의를 요구해 `spot_catalog`와 조인할 수 없다.
     *
     * 사본이므로 카탈로그 좌표가 바뀌면 배치가 함께 갱신한다. `geography` 컬럼은 매핑하지
     * 않는다 — 트리거가 이 둘에서 파생시키므로 JPA가 PostGIS 타입을 알 필요가 없다.
     */
    @Column(name = "map_x")
    var mapX: Double? = null,

    @Column(name = "map_y")
    var mapY: Double? = null,

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
 * PK가 `catalog_id`라 카탈로그당 최대 하나다.
 */
@Entity
@Table(name = "spot_catalog_festival")
class SpotCatalogFestivalEntity(
    @Id
    @Column(name = "catalog_id")
    var catalogId: Long,

    @Column(name = "event_start_date", nullable = false)
    var eventStartDate: LocalDate,

    /** 진행 중 판정의 기준. `event_end_date >= 오늘`이면 진행 중이다. */
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
