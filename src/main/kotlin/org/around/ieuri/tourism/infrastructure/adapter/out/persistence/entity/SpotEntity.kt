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

/**
 * 관광지 목록 JPA 엔티티. 도메인 모델 `Spot`과 별개 클래스이며 `mapper` 패키지가 변환한다 — 둘을 한 클래스로
 * 합치지 말 것(프로젝트 전역 규칙).
 *
 * PK는 surrogate id, upsert 단위는 자연키 (content_id, language) UNIQUE(uq_spot).
 *
 * 도메인 `Spot`에 있으나 여기 없는 필드가 둘 있고, 둘 다 의도적이다:
 *  - `distance`      : 요청 좌표에 종속되므로 적재 대상이 아니다(조회 시 계산).
 *  - `attributionText`: [copyright]에서 결정론적으로 파생되므로 컬럼으로 두면 둘이 어긋날 수 있다.
 * 반대로 여기 있으나 도메인에 없는 [contentTypeId]는 적재 전용 관측 필드다.
 */
@Entity
@Table(name = "spot")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: SpotCategory,

    /**
     * 해석 이전의 TourAPI 원본 contentTypeId. **적재 전용 — 응답·로그로 내보내지 말 것**(SPEC-0002 R8·AC-21).
     * [category] 해석 실패 시 조용히 TOURIST_SPOT으로 떨어지는 폴백을 사후에 감지·교정하기 위해 보관한다
     * (런타임 계약 위험 4번).
     */
    @Column(name = "content_type_id")
    var contentTypeId: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

    /** 이번 동기화가 이 행을 건드린 시각. 사라진 항목 정리(R-5)의 기준. */
    @Column(name = "last_synced_at", nullable = false)
    var lastSyncedAt: Instant = Instant.now(),

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
