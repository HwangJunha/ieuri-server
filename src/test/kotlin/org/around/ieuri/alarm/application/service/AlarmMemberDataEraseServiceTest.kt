package org.around.ieuri.alarm.application.service

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import kotlin.test.Test

/** 탈퇴 회원의 알림 데이터 파기(SPEC-0028 R3·AC3.1). */
class AlarmMemberDataEraseServiceTest {

    private val deviceTokenRepository = mockk<DeviceTokenRepositoryPort>(relaxed = true)
    private val preferenceRepository = mockk<NotificationPreferenceRepositoryPort>(relaxed = true)

    private val service = AlarmMemberDataEraseService(deviceTokenRepository, preferenceRepository)

    /** 기기 토큰과 수신 설정이 **둘 다** 지워진다 — 하나만 지우면 탈퇴 후 설정이 남는다. */
    @Test
    fun `탈퇴하면 기기 토큰과 수신 설정이 지워진다`() {
        service.eraseMemberData(7L)

        verify { deviceTokenRepository.deleteByMemberId(7L) }
        verify { preferenceRepository.deleteByMemberId(7L) }
    }
}
