package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant

/**
 * 앱·언어별 카테고리 노출 구성(V57). tourism과 community가 함께 읽는 유일한 출처다.
 * **표시 문구 컬럼이 없다** — 앱이 category를 언어별로 매핑한다(POL-023).
 */
@Entity
@Table(name = "app_category")
class AppCategoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "app_key", nullable = false, length = 50)
    var appKey: String,

    /** 카테고리 가용 집합이 언어마다 달라 구성도 언어별로 나뉜다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    var language: DomainLocale,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    @Column(name = "position", nullable = false)
    var position: Short,

    /** 순서를 그대로 둔 채 잠시 내릴 수 있게 한다. */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
