package org.around.ieuri.alarm.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.alarm.application.command.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.command.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** 기기 토큰 수명주기(SPEC-0028 R1·R2·R3·R20). */
class DeviceTokenCommandServiceTest {

    private val deviceTokenRepository = mockk<DeviceTokenRepositoryPort>(relaxed = true)

    private val service = DeviceTokenCommandService(deviceTokenRepository)

    private val memberId = 7L
    private val deviceId = "device-1"

    private fun command(
        fcmToken: String = "token-1",
        language: AlarmLocale = AlarmLocale.KO,
        platform: DevicePlatform = DevicePlatform.ANDROID,
    ) = RegisterDeviceTokenCommand(deviceId, fcmToken, language, platform)

    private fun stored(
        ownerId: Long = memberId,
        fcmToken: String = "token-1",
        language: AlarmLocale = AlarmLocale.KO,
    ) = DeviceToken.restore(
        id = 1L,
        deviceId = deviceId,
        memberId = ownerId,
        fcmToken = fcmToken,
        language = language,
        platform = DevicePlatform.ANDROID,
        createdAt = Instant.parse("2026-08-19T00:00:00Z"),
    )

    /** AC1.1 — 토큰이 없던 회원. */
    @Test
    fun `등록된 적 없는 기기는 새로 저장된다`() {
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns null
        val saved = slot<DeviceToken>()

        service.register(command(), memberId)

        verify { deviceTokenRepository.save(capture(saved)) }
        verify(exactly = 0) { deviceTokenRepository.update(any()) }
        assertEquals(deviceId, saved.captured.deviceId)
        assertEquals(memberId, saved.captured.memberId)
        assertEquals("token-1", saved.captured.fcmToken)
    }

    /** AC1.2 — 같은 deviceId로 다른 토큰. 행이 늘지 않고 값만 바뀐다. */
    @Test
    fun `같은 기기의 재등록은 저장하지 않고 갱신한다`() {
        val existing = stored(fcmToken = "old-token")
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns existing

        service.register(command(fcmToken = "new-token"), memberId)

        verify(exactly = 0) { deviceTokenRepository.save(any()) }
        verify { deviceTokenRepository.update(existing) }
        assertEquals("new-token", existing.fcmToken)
    }

    /** AC20.1·AC20.2 — 언어만 바뀐 재호출. */
    @Test
    fun `언어만 바뀐 재등록은 언어를 갱신하고 행을 늘리지 않는다`() {
        val existing = stored(language = AlarmLocale.KO)
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns existing

        service.register(command(language = AlarmLocale.JA), memberId)

        verify(exactly = 0) { deviceTokenRepository.save(any()) }
        assertEquals(AlarmLocale.JA, existing.language)
    }

    /** AC1.4 — 다른 회원이 쓰던 기기. 소유가 새 회원에게 옮겨져야 한다. */
    @Test
    fun `다른 회원이 쓰던 기기는 소유가 옮겨진다`() {
        val existing = stored(ownerId = 99L)
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns existing

        service.register(command(), memberId)

        verify { deviceTokenRepository.update(existing) }
        assertEquals(memberId, existing.memberId)
    }

    /** AC2.1 */
    @Test
    fun `자기 기기는 삭제된다`() {
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns stored()

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify { deviceTokenRepository.deleteByDeviceId(deviceId) }
    }

    /** AC2.2 — 멱등. 이미 없어도 예외가 아니다. */
    @Test
    fun `없는 기기를 지워도 성공한다`() {
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns null

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify(exactly = 0) { deviceTokenRepository.deleteByDeviceId(any()) }
    }

    /** 남의 기기는 지우지 않는다 — deviceId만 알면 남의 토큰을 끊을 수 있으면 안 된다. */
    @Test
    fun `다른 회원의 기기는 지우지 않는다`() {
        every { deviceTokenRepository.findByDeviceId(deviceId) } returns stored(ownerId = 99L)

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify(exactly = 0) { deviceTokenRepository.deleteByDeviceId(any()) }
    }
}
