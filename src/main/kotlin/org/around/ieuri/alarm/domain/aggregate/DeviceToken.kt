package org.around.ieuri.alarm.domain.aggregate

import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import java.time.Instant

/**
 * 한 기기의 푸시 토큰(SPEC-0028 R1). [deviceId]가 동일성 기준이고 [fcmToken]은 갱신 대상이다.
 * - 기간 만료가 없다. 삭제는 로그아웃(R2)·탈퇴(R3)·발송 실패 응답(R4)뿐이다.
 */
class DeviceToken private constructor(
    val id: Long?,
    val deviceId: String,
    memberId: Long,
    fcmToken: String,
    language: AlarmLocale,
    platform: DevicePlatform,
    val createdAt: Instant?,
) {
    /** 다른 회원이 같은 기기로 재등록하면 바뀐다(AC1.4). */
    var memberId: Long = memberId
        private set

    var fcmToken: String = fcmToken
        private set

    var language: AlarmLocale = language
        private set

    var platform: DevicePlatform = platform
        private set

    /** 같은 기기의 재등록을 반영한다(R1·R20·AC1.4). 소유 회원까지 덮어쓴다. */
    fun reregister(
        memberId: Long,
        fcmToken: String,
        language: AlarmLocale,
        platform: DevicePlatform,
    ) {
        this.memberId = requireValidMemberId(memberId)
        this.fcmToken = requireValidToken(fcmToken)
        this.language = language
        this.platform = platform
    }

    fun ownedBy(memberId: Long): Boolean = this.memberId == memberId

    companion object {
        fun create(
            deviceId: String,
            memberId: Long,
            fcmToken: String,
            language: AlarmLocale,
            platform: DevicePlatform,
        ): DeviceToken = DeviceToken(
            id = null,
            deviceId = requireValidDeviceId(deviceId),
            memberId = requireValidMemberId(memberId),
            fcmToken = requireValidToken(fcmToken),
            language = language,
            platform = platform,
            createdAt = null,
        )

        fun restore(
            id: Long,
            deviceId: String,
            memberId: Long,
            fcmToken: String,
            language: AlarmLocale,
            platform: DevicePlatform,
            createdAt: Instant,
        ): DeviceToken = DeviceToken(id, deviceId, memberId, fcmToken, language, platform, createdAt)

        private fun requireValidDeviceId(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "deviceId는 비어 있을 수 없다." }
            return trimmed
        }

        private fun requireValidToken(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "fcmToken은 비어 있을 수 없다." }
            return trimmed
        }

        private fun requireValidMemberId(value: Long): Long {
            require(value > 0) { "memberId는 양수여야 한다." }
            return value
        }
    }
}
