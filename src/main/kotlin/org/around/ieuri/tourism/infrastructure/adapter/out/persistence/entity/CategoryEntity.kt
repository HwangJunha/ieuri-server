package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.io.Serializable
import java.time.Instant

/**
 * 카테고리 정의(V58). **코드가 PK다** — 앱·다른 도메인의 저장값이 이 문자열이라 바꾸지 않는다.
 *
 * 표시 문구는 언어에 종속되므로 [CategoryTranslationEntity]에만 있다.
 */
@Entity
@Table(name = "category")
class CategoryEntity(
    @Id
    @Column(name = "code", nullable = false, length = 30)
    var code: String,

    /** 칩·마커 이미지. 없으면 클라이언트 기본 표현으로 떨어진다. */
    @Column(name = "icon_url", length = 500)
    var iconUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)

/** [CategoryTranslationEntity]의 복합 PK. */
data class CategoryTranslationId(
    var categoryCode: String = "",
    var language: DomainLocale? = null,
) : Serializable

/** 카테고리의 언어별 표시 문구(V58). 앱이 모르는 카테고리도 이름을 그릴 수 있게 한다. */
@Entity
@Table(name = "category_translation")
@IdClass(CategoryTranslationId::class)
class CategoryTranslationEntity(
    @Id
    @Column(name = "category_code", nullable = false, length = 30)
    var categoryCode: String,

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
)
