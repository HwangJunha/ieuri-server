package org.around.ieuri.alarm.domain.device

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
    ) = DeviceToken.register(deviceId, memberId, fcmToken, language, platform)

    @Test
    fun `deviceId가 비면 만들 수 없다`() {
        val e = assertFailsWith<IllegalArgumentException> { token(deviceId = "   ") }
        assertEquals("deviceId는 비어 있을 수 없다.", e.message)
    }

    @Test
    fun `fcmToken이 비면 만들 수 없다`() {
        val e = assertFailsWith<IllegalArgumentException> { token(fcmToken = "") }
        assertEquals("fcmToken은 비어 있을 수 없다.", e.message)
    }

    @Test
    fun `memberId가 양수가 아니면 만들 수 없다`() {
        val e = assertFailsWith<IllegalArgumentException> { token(memberId = 0L) }
        assertEquals("memberId는 양수여야 한다.", e.message)
    }

    /** 검사 순서가 응답 메시지를 정한다(deviceId → memberId → fcmToken). */
    @Test
    fun `여러 값이 틀리면 deviceId부터 알린다`() {
        val e = assertFailsWith<IllegalArgumentException> { token(deviceId = "", memberId = 0L, fcmToken = "") }
        assertEquals("deviceId는 비어 있을 수 없다.", e.message)
    }

    @Test
    fun `deviceId와 fcmToken의 앞뒤 공백은 제거된다`() {
        val created = token(deviceId = "  device-1  ", fcmToken = "  token-1  ")

        assertEquals("device-1", created.deviceId)
        assertEquals("token-1", created.fcmToken)
    }

    /** R20 — 언어만 바뀐 재호출. 원본은 바뀌지 않는다. */
    @Test
    fun `재등록이 언어를 갱신한 새 값을 돌려준다`() {
        val existing = token(language = AlarmLocale.KO)

        val updated = existing.reregister(1L, "token-1", AlarmLocale.JA, DevicePlatform.ANDROID)

        assertEquals(AlarmLocale.JA, updated.language)
        assertEquals(AlarmLocale.KO, existing.language)
    }

    /** R1 — 토큰 로테이션. 앞뒤 공백은 제거된다. */
    @Test
    fun `재등록이 토큰을 갱신한다`() {
        val updated = token(fcmToken = "old-token").reregister(1L, "  new-token  ", AlarmLocale.KO, DevicePlatform.ANDROID)

        assertEquals("new-token", updated.fcmToken)
    }

    /** AC1.4 — 기기 소유가 옮겨진다. deviceId·id는 그대로다. */
    @Test
    fun `재등록이 소유 회원을 옮긴다`() {
        val existing = token(memberId = 1L).copy(id = 7L)

        val updated = existing.reregister(2L, "token-1", AlarmLocale.KO, DevicePlatform.ANDROID)

        assertEquals(2L, updated.memberId)
        assertEquals(7L, updated.id)
        assertEquals("device-1", updated.deviceId)
        assertTrue(updated.ownedBy(2L))
        assertFalse(updated.ownedBy(1L))
    }

    @Test
    fun `재등록이 플랫폼을 갱신한다`() {
        val updated = token(platform = DevicePlatform.ANDROID).reregister(1L, "token-1", AlarmLocale.KO, DevicePlatform.IOS)

        assertEquals(DevicePlatform.IOS, updated.platform)
    }

    @Test
    fun `재등록도 빈 토큰과 잘못된 회원을 거부한다`() {
        val existing = token()

        assertEquals("fcmToken은 비어 있을 수 없다.", assertFailsWith<IllegalArgumentException> { existing.reregister(1L, "  ", AlarmLocale.KO, DevicePlatform.ANDROID) }.message)
        assertEquals("memberId는 양수여야 한다.", assertFailsWith<IllegalArgumentException> { existing.reregister(0L, "t", AlarmLocale.KO, DevicePlatform.ANDROID) }.message)
    }
}
