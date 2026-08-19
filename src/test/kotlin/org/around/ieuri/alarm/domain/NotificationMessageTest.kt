package org.around.ieuri.alarm.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.NotificationType

/** 알림 문구 불변식(SPEC-0028 R13). */
class NotificationMessageTest {

    private fun message(title: String = "제목", body: String = "본문") =
        NotificationMessage.restore(1L, NotificationType.DM, AlarmLocale.KO, title, body)

    @Test
    fun `제목과 본문의 앞뒤 공백은 제거된다`() {
        val restored = message(title = "  제목  ", body = "  본문  ")

        assertEquals("제목", restored.title)
        assertEquals("본문", restored.body)
    }

    @Test
    fun `제목이 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { message(title = "   ") }
    }

    /**
     * **본문도 비울 수 없다.** iOS(APNs)가 본문 없는 알림을 표시하지 않으므로, 빈 본문을
     * 허용하면 그 언어의 iOS 알림이 오류 없이 조용히 사라진다.
     */
    @Test
    fun `본문이 비면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { message(body = "") }
        assertFailsWith<IllegalArgumentException> { message(body = "   ") }
    }
}
