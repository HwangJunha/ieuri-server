package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 축제 전용 정보 JPA 엔티티. `spot`의 자식이며 적재 단위는 `spot_id`다(V20).
 *
 * 적재는 upsert라 [org.around.ieuri.tourism.infrastructure.adapter.out.persistence.FestivalUpsertJdbcAdapter]가
 * 담당하고, 이 엔티티는 조회·삭제의 QueryDSL 대상이자 `ddl-auto: validate`의 스키마 대조 기준이다.
 */
@Entity
@Table(name = "spot_festival")
class SpotFestivalEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 부모 `spot` 행의 surrogate PK. 연관이 아니라 plain 컬럼이다(애그리거트 간 참조는 id로 한다). */
    @Column(name = "spot_id", nullable = false)
    var spotId: Long,

    @Column(name = "event_start_date", nullable = false)
    var eventStartDate: LocalDate,

    /** 정리 판정의 기준. `event_end_date < 실행일`이면 종료된 축제다. */
    @Column(name = "event_end_date", nullable = false)
    var eventEndDate: LocalDate,

    @Column(name = "progress_type", length = 50)
    var progressType: String? = null,

    @Column(name = "festival_type", length = 50)
    var festivalType: String? = null,

    /** 이번 동기화가 이 행을 건드린 시각. */
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
