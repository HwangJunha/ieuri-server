package org.around.ieuri.alarm.domain.push

import org.around.ieuri.alarm.domain.device.AlarmLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 알림 문구 불변식(SPEC-0028 R13). */
class NotificationMessageTest {

    private fun message(title: String = "제목", body: String = "본문") = NotificationMessage.of(NotificationType.DM, AlarmLocale.KO, title, body)

    @Test
    fun `제목과 본문의 앞뒤 공백은 제거된다`() {
        val read = message(title = "  제목  ", body = "  본문  ")

        assertEquals("제목", read.title)
        assertEquals("본문", read.body)
    }

    @Test
    fun `제목이 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { message(title = "   ") }
    }

    /** 본문도 비울 수 없다(iOS가 본문 없는 알림을 표시하지 않는다). */
    @Test
    fun `본문이 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { message(body = "") }
        assertFailsWith<IllegalArgumentException> { message(body = "   ") }
    }
}
