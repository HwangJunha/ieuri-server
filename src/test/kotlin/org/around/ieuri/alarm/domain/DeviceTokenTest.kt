package org.around.ieuri.alarm.domain

import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기기 토큰 불변식과 재등록 규칙(SPEC-0028 R1·R20·AC1.4). */
class DeviceTokenTest {

    private fun token(
        deviceId: String = "device-1",
        memberId: Long = 1L,
        fcmToken: String = "token-1",
        language: AlarmLocale = AlarmLocale.KO,
        platform: DevicePlatform = DevicePlatform.ANDROID,
    ) = DeviceToken.create(deviceId, memberId, fcmToken, language, platform)

    @Test
    fun `deviceId가 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { token(deviceId = "   ") }
    }

    @Test
    fun `fcmToken이 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { token(fcmToken = "") }
    }

    @Test
    fun `memberId가 양수가 아니면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { token(memberId = 0L) }
    }

    @Test
    fun `deviceId와 fcmToken의 앞뒤 공백은 제거된다`() {
        val created = token(deviceId = "  device-1  ", fcmToken = "  token-1  ")

        assertEquals("device-1", created.deviceId)
        assertEquals("token-1", created.fcmToken)
    }

    /** R20 — 언어만 바뀐 재호출. */
    @Test
    fun `재등록이 언어를 갱신한다`() {
        val existing = token(language = AlarmLocale.KO)

        existing.reregister(1L, "token-1", AlarmLocale.JA, DevicePlatform.ANDROID)

        assertEquals(AlarmLocale.JA, existing.language)
    }

    /** R1 — 토큰 로테이션. */
    @Test
    fun `재등록이 토큰을 갱신한다`() {
        val existing = token(fcmToken = "old-token")

        existing.reregister(1L, "new-token", AlarmLocale.KO, DevicePlatform.ANDROID)

        assertEquals("new-token", existing.fcmToken)
    }

    /** AC1.4 — 기기 소유가 옮겨진다. */
    @Test
    fun `재등록이 소유 회원을 옮긴다`() {
        val existing = token(memberId = 1L)

        existing.reregister(2L, "token-1", AlarmLocale.KO, DevicePlatform.ANDROID)

        assertEquals(2L, existing.memberId)
        assertTrue(existing.ownedBy(2L))
        assertFalse(existing.ownedBy(1L))
    }

    @Test
    fun `재등록이 플랫폼을 갱신한다`() {
        val existing = token(platform = DevicePlatform.ANDROID)

        existing.reregister(1L, "token-1", AlarmLocale.KO, DevicePlatform.IOS)

        assertEquals(DevicePlatform.IOS, existing.platform)
    }

    @Test
    fun `재등록도 빈 토큰을 거부한다`() {
        val existing = token()

        assertFailsWith<IllegalArgumentException> {
            existing.reregister(1L, "  ", AlarmLocale.KO, DevicePlatform.ANDROID)
        }
    }
}
