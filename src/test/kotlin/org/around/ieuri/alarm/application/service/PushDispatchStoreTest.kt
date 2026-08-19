package org.around.ieuri.alarm.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationMessageRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType

/** 발송 경로의 DB 접근(SPEC-0028 R4·R10). */
class PushDispatchStoreTest {

    private val deviceTokenRepository = mockk<DeviceTokenRepositoryPort>(relaxed = true)
    private val preferenceRepository = mockk<NotificationPreferenceRepositoryPort>()
    private val messageRepository = mockk<NotificationMessageRepositoryPort>()

    private val store = PushDispatchStore(
        deviceTokenRepository,
        preferenceRepository,
        messageRepository,
    )

    private val memberId = 1L

    /** AC10.3 — 설정을 바꾼 적 없는 회원은 켜짐이다. */
    @Test
    fun `설정 행이 없으면 켜진 것으로 본다`() {
        every {
            preferenceRepository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE)
        } returns null

        assertTrue(store.categoryEnabled(memberId, NotificationCategory.MESSAGE))
    }

    @Test
    fun `설정 행이 있으면 그 값을 따른다`() {
        every {
            preferenceRepository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE)
        } returns NotificationPreference.restore(1L, memberId, NotificationCategory.MESSAGE, false)

        assertFalse(store.categoryEnabled(memberId, NotificationCategory.MESSAGE))
    }

    /** 지울 것이 없으면 삭제를 부르지 않는다 — 빈 IN 절이 나가지 않게 한다. */
    @Test
    fun `무효 토큰이 없으면 삭제하지 않는다`() {
        store.removeInvalidTokens(emptyList())

        verify(exactly = 0) { deviceTokenRepository.deleteByDeviceIds(any()) }
    }

    /** R4 — 판정된 기기만 지운다. */
    @Test
    fun `무효 토큰을 지운다`() {
        store.removeInvalidTokens(listOf("d1", "d2"))

        verify { deviceTokenRepository.deleteByDeviceIds(listOf("d1", "d2")) }
    }

    @Test
    fun `수신자의 기기를 조회한다`() {
        every { deviceTokenRepository.findByMemberId(memberId) } returns emptyList()

        assertEquals(emptyList(), store.findTokens(memberId))
        verify { deviceTokenRepository.findByMemberId(memberId) }
    }

    /** 언어로 바로 찾을 수 있게 맵으로 접는다 — 발송이 언어별로 갈리기 때문이다. */
    @Test
    fun `문구를 언어별 맵으로 돌려준다`() {
        every { messageRepository.findByType(NotificationType.DM) } returns listOf(
            NotificationMessage.restore(1L, NotificationType.DM, AlarmLocale.KO, "한국어제목"),
            NotificationMessage.restore(2L, NotificationType.DM, AlarmLocale.EN, "영어제목"),
        )

        val titles = store.findTitles(NotificationType.DM)

        assertEquals("한국어제목", titles[AlarmLocale.KO])
        assertEquals("영어제목", titles[AlarmLocale.EN])
        assertEquals(2, titles.size)
    }

    @Test
    fun `문구가 없으면 빈 맵이다`() {
        every { messageRepository.findByType(NotificationType.DM) } returns emptyList()

        assertEquals(emptyMap(), store.findTitles(NotificationType.DM))
    }
}
