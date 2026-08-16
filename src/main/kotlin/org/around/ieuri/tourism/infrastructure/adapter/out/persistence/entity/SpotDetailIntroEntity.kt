package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import org.around.ieuri.common.persistence.DbSchema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 관광지 운영 정보 JPA 엔티티. 카테고리별로 upstream 응답이 갈리므로 `category`가 키에 남는다.
 *
 * 전 컬럼이 null인 행은 "조회했으나 값 없음"을 뜻한다 — 미적재와 구분된다.
 */
@Entity
@Table(name = "spot_detail_intro", schema = DbSchema.TOUR)
class SpotDetailIntroEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    @Column(name = "business_hours")
    var businessHours: String? = null,

    @Column(name = "closed_days")
    var closedDays: String? = null,

    @Column(name = "parking")
    var parking: String? = null,

    @Column(name = "inquiry_phone")
    var inquiryPhone: String? = null,

    @Column(name = "fee")
    var fee: String? = null,

    @Column(name = "pet_allowed")
    var petAllowed: String? = null,

    @Column(name = "credit_card_accepted")
    var creditCardAccepted: String? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
