package org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity

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
import java.time.Instant
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.common.persistence.DbSchema

/**
 * 기기 푸시 토큰(SPEC-0028 R1).
 *
 * 다른 도메인 엔티티와 같은 규약이다 — 연관(@ManyToOne·@OneToMany)을 두지 않고 plain
 * 컬럼으로 id만 든다. `member_id`는 스키마가 달라 FK도 아니며, 탈퇴 연쇄는 애플리케이션이
 * 이벤트를 받아 지운다.
 */
@Entity
@Table(name = "device_token", schema = DbSchema.ALARM)
class DeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 재등록 판별 키. 유니크는 이 컬럼 단독이다(AC1.4). */
    @Column(name = "device_id", nullable = false, length = 255)
    var deviceId: String,

    /** 재등록으로 바뀔 수 있다 — 기기를 다른 회원이 쓰기 시작하는 경우다(AC1.4). */
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    var fcmToken: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    var language: AlarmLocale,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    var platform: DevicePlatform,

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

/**
 * 카테고리별 수신 설정(SPEC-0028 R19).
 *
 * **행이 없는 것이 정상이며 켜짐을 뜻한다**(AC10.3) — 사용자가 토글을 처음 건드릴 때만 생긴다.
 */
@Entity
@Table(name = "notification_preference", schema = DbSchema.ALARM)
class NotificationPreferenceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    var category: NotificationCategory,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,

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
