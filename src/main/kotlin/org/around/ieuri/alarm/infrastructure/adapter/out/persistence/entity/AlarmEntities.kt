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
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType
import org.around.ieuri.common.persistence.DbSchema
import java.time.Instant

/** 기기 푸시 토큰(SPEC-0028 R1). `member_id`는 FK가 아니며 탈퇴 연쇄는 이벤트로 지운다. */
@Entity
@Table(name = "device_token", schema = DbSchema.ALARM)
class DeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 재등록 판별 키. 유니크는 이 컬럼 단독이다(AC1.4). */
    @Column(name = "device_id", nullable = false, length = 255)
    var deviceId: String,

    /** 다른 회원이 같은 기기로 재등록하면 바뀐다(AC1.4). */
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

/** 알림 문구(SPEC-0028 R13). 읽기 전용이며 값은 마이그레이션·운영자가 넣는다. */
@Entity
@Table(name = "notification_message", schema = DbSchema.ALARM)
class NotificationMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    var notificationType: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(name = "locale", nullable = false, length = 10)
    var locale: AlarmLocale,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    /** 서버가 정한 안내 문구. 비우면 iOS가 알림을 표시하지 않는다. */
    @Column(name = "body", nullable = false, length = 200)
    var body: String,

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

/** 카테고리별 수신 설정(SPEC-0028 R19). 행이 없으면 켜짐이다(AC10.3). */
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
