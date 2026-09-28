package org.around.ieuri.community.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant

/** 주인을 잃은 사진 오브젝트 키(SPEC-0021 R6). 마이그레이션이 채운 읽기 전용 목록이다. */
@Entity
@Table(name = "community_orphan_photo_object", schema = DbSchema.COMMUNITY)
class OrphanPhotoObjectEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "object_key", nullable = false, length = 512)
    var objectKey: String,

    /** 키의 출처. */
    @Column(name = "source", nullable = false, length = 32)
    var source: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
)
