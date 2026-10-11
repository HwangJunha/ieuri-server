package org.around.ieuri.alarm.application.device

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.alarm.application.device.provided.dto.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.device.provided.dto.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.device.required.DeviceTokenRepository
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform
import org.around.ieuri.alarm.domain.device.DeviceToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 기기 토큰 수명주기(SPEC-0028 R1~R4·R20). */
class DeviceTokenModifyServiceTest {

    private val repository = mockk<DeviceTokenRepository>(relaxed = true)

    private val service = DeviceTokenModifyService(repository)

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
    ) = DeviceToken(id = 1L, deviceId = deviceId, memberId = ownerId, fcmToken = fcmToken, language = language, platform = DevicePlatform.ANDROID)

    /** AC1.1 — 토큰이 없던 회원. id 없이 저장한다(새 행). */
    @Test
    fun `등록된 적 없는 기기는 새로 저장된다`() {
        every { repository.findByDeviceId(deviceId) } returns null
        val saved = slot<DeviceToken>()

        service.register(command(), memberId)

        verify { repository.save(capture(saved)) }
        assertNull(saved.captured.id)
        assertEquals(deviceId, saved.captured.deviceId)
        assertEquals(memberId, saved.captured.memberId)
        assertEquals("token-1", saved.captured.fcmToken)
    }

    /** AC1.2 — 같은 deviceId로 다른 토큰. 같은 행(id)을 새 값으로 저장한다. */
    @Test
    fun `같은 기기의 재등록은 같은 행을 새 토큰으로 저장한다`() {
        every { repository.findByDeviceId(deviceId) } returns stored(fcmToken = "old-token")
        val saved = slot<DeviceToken>()

        service.register(command(fcmToken = "new-token"), memberId)

        verify(exactly = 1) { repository.save(capture(saved)) }
        assertEquals(1L, saved.captured.id)
        assertEquals("new-token", saved.captured.fcmToken)
    }

    /** AC20.1·AC20.2 — 언어만 바뀐 재호출. */
    @Test
    fun `언어만 바뀐 재등록은 언어를 갱신한다`() {
        every { repository.findByDeviceId(deviceId) } returns stored(language = AlarmLocale.KO)
        val saved = slot<DeviceToken>()

        service.register(command(language = AlarmLocale.JA), memberId)

        verify { repository.save(capture(saved)) }
        assertEquals(AlarmLocale.JA, saved.captured.language)
        assertEquals(1L, saved.captured.id)
    }

    /** AC1.4 — 다른 회원이 쓰던 기기. 소유가 새 회원에게 옮겨진다. */
    @Test
    fun `다른 회원이 쓰던 기기는 소유가 옮겨진다`() {
        every { repository.findByDeviceId(deviceId) } returns stored(ownerId = 99L)
        val saved = slot<DeviceToken>()

        service.register(command(), memberId)

        verify { repository.save(capture(saved)) }
        assertEquals(memberId, saved.captured.memberId)
    }

    /** AC2.1 */
    @Test
    fun `자기 기기는 삭제된다`() {
        every { repository.findByDeviceId(deviceId) } returns stored()

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify { repository.deleteByDeviceId(deviceId) }
    }

    /** AC2.2 — 멱등. 이미 없어도 예외가 아니다. */
    @Test
    fun `없는 기기를 지워도 성공한다`() {
        every { repository.findByDeviceId(deviceId) } returns null

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify(exactly = 0) { repository.deleteByDeviceId(any()) }
    }

    @Test
    fun `다른 회원의 기기는 지우지 않는다`() {
        every { repository.findByDeviceId(deviceId) } returns stored(ownerId = 99L)

        service.delete(DeleteDeviceTokenCommand(deviceId), memberId)

        verify(exactly = 0) { repository.deleteByDeviceId(any()) }
    }

    /** R3·AC3.1 */
    @Test
    fun `탈퇴하면 회원의 기기 토큰이 지워진다`() {
        service.eraseMemberTokens(7L)

        verify { repository.deleteByMemberId(7L) }
    }

    /** R4 — 무효 판정이 없으면 저장소를 부르지 않는다. */
    @Test
    fun `무효 토큰만 지우고 빈 목록이면 아무것도 하지 않는다`() {
        service.removeInvalid(emptyList())
        verify(exactly = 0) { repository.deleteByDeviceIds(any()) }

        service.removeInvalid(listOf("dead-1", "dead-2"))
        verify { repository.deleteByDeviceIds(listOf("dead-1", "dead-2")) }
    }
}
