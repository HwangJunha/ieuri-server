package org.around.ieuri.alarm.application.push

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.alarm.application.push.required.NotificationMessageRepository
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals

/** 알림 문구 조회(SPEC-0028 R13). */
class NotificationMessageQueryServiceTest {

    private val repository = mockk<NotificationMessageRepository>()
    private val service = NotificationMessageQueryService(repository)

    /** 문구를 언어 키 맵으로 접는다. */
    @Test
    fun `문구를 언어별 맵으로 돌려준다`() {
        every { repository.findByType(NotificationType.DM) } returns listOf(
            NotificationMessage.of(NotificationType.DM, AlarmLocale.KO, "한국어제목", "한국어본문"),
            NotificationMessage.of(NotificationType.DM, AlarmLocale.EN, "영어제목", "영어본문"),
        )

        val found = service.messagesOf(NotificationType.DM)

        assertEquals("한국어제목", found[AlarmLocale.KO]?.title)
        assertEquals("한국어본문", found[AlarmLocale.KO]?.body)
        assertEquals("영어제목", found[AlarmLocale.EN]?.title)
        assertEquals(2, found.size)
    }

    @Test
    fun `문구가 없으면 빈 맵이다`() {
        every { repository.findByType(NotificationType.DM) } returns emptyList()

        assertEquals(emptyMap(), service.messagesOf(NotificationType.DM))
    }
}
