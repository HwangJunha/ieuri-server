package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import org.around.ieuri.tourism.domain.vo.CopyrightType
import java.time.Instant

/**
 * 관광지 상세 본문 JPA 엔티티(upstream 응답의 적재 계층). 쓰기(upsert)는 SpotDetailUpsertJdbcAdapter가 한다.
 */
@Entity
@Table(name = "spot_detail", schema = DbSchema.TOUR)
class SpotDetailEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    @Column(name = "category", nullable = false, length = 30)
    var category: String,

    @Column(name = "thumbnail_image_url", nullable = false, length = 500)
    var thumbnailImageUrl: String,

    @Column(name = "overview")
    var overview: String? = null,

    @Column(name = "address", length = 500)
    var address: String? = null,

    @Column(name = "homepage")
    var homepage: String? = null,

    @Column(name = "tel", length = 255)
    var tel: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

    /** upstream에 본문이 없음을 확인한 행. 부재와 미적재를 가른다. */
    @Column(name = "body_absent", nullable = false)
    var bodyAbsent: Boolean = false,

    /** 부재 행의 신선도 판정 기준. */
    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
