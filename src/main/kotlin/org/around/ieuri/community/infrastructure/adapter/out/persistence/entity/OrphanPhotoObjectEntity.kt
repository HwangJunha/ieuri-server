package org.around.ieuri.community.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 주인을 잃은 사진 오브젝트 키(SPEC-0021 R6). 피드 테이블을 지우기 전에 마이그레이션이
 * 채워 둔 목록이며, 스토리지를 비운 뒤(ieuri-server#83) 함께 사라진다.
 *
 * 도메인 애그리게이트가 없다 — 지울 목록일 뿐 불변식이 없어 값을 읽는 경로만 있으면 된다.
 */
@Entity
@Table(name = "community_orphan_photo_object")
class OrphanPhotoObjectEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "object_key", nullable = false, length = 512)
    var objectKey: String,

    /** 어디서 나온 키인지. 지금은 피드뿐이다. */
    @Column(name = "source", nullable = false, length = 32)
    var source: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,
)
