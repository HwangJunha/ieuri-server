package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.io.Serializable
import java.time.Instant

/**
 * 카테고리 정의(V58·V59). 카테고리를 코드에서 데이터로 옮긴 결과이며, 새 카테고리는 행을 넣는
 * 것으로 끝난다 — 서버 배포도 앱 업데이트도 필요 없다.
 *
 * **PK는 대리 키다**(V59) — [code]를 PK로 두면 코드가 곧 신원이라 오타 하나 고치는 데 참조하는
 * 모든 행을 함께 갱신해야 한다. code는 UNIQUE로 남아 조회 축을 유지한다.
 */
@Entity
@Table(name = "category")
class CategoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 안정 식별자. 클라이언트·다른 도메인이 주고받는 값이다. */
    @Column(name = "code", nullable = false, length = 30)
    var code: String,

    /** 칩·마커 이미지. 없으면 클라이언트 기본 표현으로 떨어진다. */
    @Column(name = "icon_url", length = 500)
    var iconUrl: String? = null,

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

/** [CategoryTranslationEntity]의 복합 PK. */
data class CategoryTranslationId(
    var categoryId: Long? = null,
    var language: DomainLocale? = null,
) : Serializable

/** 카테고리의 언어별 표시 문구(V58). 앱이 모르는 카테고리도 이름을 그릴 수 있게 한다. */
@Entity
@Table(name = "category_translation")
@IdClass(CategoryTranslationId::class)
class CategoryTranslationEntity(
    @Id
    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "label", nullable = false, length = 50)
    var label: String,

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
 * 우리 카테고리 ↔ 외부 제공자 코드(V59). TourAPI의 contentTypeId가 첫 사용처다.
 *
 * 설정(application.yaml)이 아니라 테이블인 이유는, 설정에 있으면 신규 카테고리의 spot을
 * **수집**하려 할 때마다 배포가 필요하기 때문이다 — 저장·조회는 데이터로 되는데 수집만
 * 배포가 필요한 반쪽이 된다.
 *
 * (제공자, 코드셋)마다 코드가 갈리며 **비어 있는 칸은 행이 없다** — 여행코스는 외국어
 * 서비스에, 교통은 국문 서비스에 대응 코드가 없다.
 */
@Entity
@Table(name = "category_external_mapping")
class CategoryExternalMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    /** 외부 체계 식별자. 지금은 TOUR_API 하나다. */
    @Column(name = "provider", nullable = false, length = 30)
    var provider: String,

    /** 제공자 안에서 코드가 갈리는 축(TourAPI는 KO/FOREIGN). 언어가 아니다 — EN·JA·ZH가 같은 값을 쓴다. */
    @Column(name = "codeset", nullable = false, length = 20)
    var codeset: String,

    /** 외부 체계의 코드. TourAPI는 숫자지만 제공자마다 다를 수 있어 문자열이다. */
    @Column(name = "external_code", nullable = false, length = 30)
    var externalCode: String,

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
