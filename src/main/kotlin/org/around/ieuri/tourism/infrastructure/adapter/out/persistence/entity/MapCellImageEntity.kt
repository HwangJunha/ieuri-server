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
import org.around.ieuri.common.persistence.DbSchema
import org.around.ieuri.tourism.domain.vo.CellImageKind
import org.around.ieuri.tourism.domain.vo.CopyrightType
import java.time.Instant

/**
 * 칸 대표 이미지 JPA 엔티티(SPEC-0030 R9·R10).
 *
 * **`attributionText` 컬럼이 없다.** 출처 문구는 [CopyrightType]이 소유하며 저장하지 않는다 —
 * 적재해 두면 둘이 어긋난다(기존 `SpotImage`의 규약과 같다).
 *
 * `displayOrder` 0이 대표다. 이미지가 없는 칸은 이 표에 행이 없을 뿐이며 조회에서 빠지지
 * 않는다(R10).
 */
@Entity
@Table(name = "map_cell_image", schema = DbSchema.TOUR)
class MapCellImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "cell_id", nullable = false)
    var cellId: Long,

    /** 0이 대표. 칸 안에서 유일하다. */
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

    @Column(name = "image_url", nullable = false, length = 500)
    var imageUrl: String,

    @Column(name = "thumbnail_url", length = 500)
    var thumbnailUrl: String? = null,

    @Column(name = "caption", nullable = false, length = 255)
    var caption: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    var kind: CellImageKind,

    @Enumerated(EnumType.STRING)
    @Column(name = "copyright", nullable = false, length = 20)
    var copyright: CopyrightType,

    /** TourAPI contentid. 출처 추적용이며 조회 조건이 아니다. */
    @Column(name = "content_id", length = 20)
    var contentId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        if (updatedAt == null) updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
