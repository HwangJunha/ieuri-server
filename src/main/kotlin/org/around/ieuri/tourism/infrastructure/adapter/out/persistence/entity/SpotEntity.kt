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
 * 관광지 목록 JPA 엔티티(SPEC-0004 R-1). **도메인 모델 Spot과 별개 클래스이며 어댑터가 매핑한다**(AC-51) —
 * `Spot`은 순수 Kotlin로 남고 여기에만 JPA가 붙는다. 둘을 한 클래스로 합치지 말 것(프로젝트 전역 규칙).
 *
 * **현재 이 엔티티로 읽고 쓰는 코드는 없다.** 적재는 성능상 네이티브 배치 upsert로 하고
 * ([SpotJpaAdapter]), 조회 경로는 아직 붙지 않았다(반경 조회 방식이 `[AMEND]` 회신 대기 중).
 * 그럼에도 남겨 두는 이유는 `ddl-auto: validate`가 부팅 시 이 엔티티를 실제 `spot` 스키마와 대조해
 * **마이그레이션과 코드의 어긋남을 조기에 잡아 주기 때문**이다. 조회 경로(설계 문서 §7의 7번)가
 * 붙으면 QueryDSL 프로젝션의 대상이 된다. 그때까지는 스키마 검증이 이 클래스의 유일한 역할이다.
 *
 * PK는 surrogate id, upsert 단위는 자연키 (content_id, language) UNIQUE(uq_spot) — spot_reaction과 동일 관례.
 *
 * 도메인 `Spot`에 있으나 여기 없는 필드가 둘 있고, 둘 다 의도적이다:
 *  - `distance`      : 요청 좌표에 종속되므로 적재 대상이 아니다(조회 시 계산).
 *  - `attributionText`: [copyright]에서 결정론적으로 파생되므로 컬럼으로 두면 둘이 어긋날 수 있다.
 * 반대로 여기 있으나 도메인에 없는 [contentTypeId]는 적재 전용 관측 필드다(아래 주석 참조).
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
