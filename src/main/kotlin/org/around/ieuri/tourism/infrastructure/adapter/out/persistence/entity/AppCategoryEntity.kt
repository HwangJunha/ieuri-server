package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import org.around.ieuri.common.persistence.DbSchema
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
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant

/**
 * 앱·언어별 카테고리 노출 구성(V57). tourism과 community가 함께 읽는 유일한 출처다.
 * 표시 문구는 `category_translation`이 갖는다(V58) — 이 표는 노출 여부와 순서만 정한다.
 */
@Entity
@Table(name = "app_category", schema = DbSchema.CATEGORY)
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

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    /**
     * 노출 여부. **순서는 담지 않는다**(V59) — 서버는 무엇을 보일지만 정하고 배열 순서는
     * 클라이언트가 자기 화면 기준으로 정한다.
     */
    @Column(name = "active", nullable = false)
    var active: Boolean = true,

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