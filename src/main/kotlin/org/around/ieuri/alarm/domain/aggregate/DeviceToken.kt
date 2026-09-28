package org.around.ieuri.alarm.domain.aggregate

import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import java.time.Instant

/**
 * 한 기기의 푸시 토큰(SPEC-0028 R1).
 *
 * **[deviceId]가 동일성 기준이고 [fcmToken]은 갱신 대상이다.** FCM 토큰은 로테이션되므로
 * 키로 삼으면 로테이션마다 행이 쌓이고 죽은 토큰으로 계속 발송하게 된다.
 *
 * 기간 만료를 두지 않는다 — 삭제는 로그아웃(R2)·탈퇴(R3)·발송 실패 응답(R4) 셋뿐이다.
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
    /** 재등록으로 소유가 바뀔 수 있다(AC1.4) — 기기를 다른 회원이 쓰기 시작하는 경우다. */
    var memberId: Long = memberId
        private set

    var fcmToken: String = fcmToken
        private set

    var language: AlarmLocale = language
        private set

    var platform: DevicePlatform = platform
        private set

    /**
     * 같은 기기의 재등록을 반영한다(R1·R20·AC1.4).
     *
     * 토큰·언어·플랫폼은 물론 **소유 회원까지 덮어쓴다.** 한 기기를 다른 사람이 쓰기
     * 시작했을 때 이전 회원 귀속이 남으면 그 사람에게 발송된다.
     */
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
