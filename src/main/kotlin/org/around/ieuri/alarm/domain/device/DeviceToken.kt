package org.around.ieuri.alarm.domain.device

/**
 * 한 기기의 푸시 토큰(SPEC-0028 R1). [deviceId]가 동일성 기준이고 [fcmToken]은 갱신 대상이다.
 * - 기간 만료가 없다. 삭제는 로그아웃(R2)·탈퇴(R3)·발송 실패 응답(R4)뿐이다.
 * - 새 토큰은 [register]로 만든다 — 값의 앞뒤 공백을 걷고 검증한다.
 */
data class DeviceToken(
    val id: Long?,
    val deviceId: String,
    /** 다른 회원이 같은 기기로 재등록하면 바뀐다(AC1.4). */
    val memberId: Long,
    val fcmToken: String,
    val language: AlarmLocale,
    val platform: DevicePlatform,
) {
    /** 같은 기기의 재등록을 반영한다(R1·R20·AC1.4). 소유 회원까지 덮어쓴다. */
    fun reregister(
        memberId: Long,
        fcmToken: String,
        language: AlarmLocale,
        platform: DevicePlatform,
    ): DeviceToken = copy(
        memberId = requireValidMemberId(memberId),
        fcmToken = requireValidToken(fcmToken),
        language = language,
        platform = platform,
    )

    fun ownedBy(memberId: Long): Boolean = this.memberId == memberId

    companion object {
        fun register(
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
        )

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
