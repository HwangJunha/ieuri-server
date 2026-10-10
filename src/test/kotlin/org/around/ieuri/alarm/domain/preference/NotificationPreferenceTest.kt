package org.around.ieuri.alarm.domain.preference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 카테고리별 수신 설정(SPEC-0028 R19·AC10.3·AC19.1). */
class NotificationPreferenceTest {

    @Test
    fun `저장된 행이 없으면 모든 카테고리가 켜짐이고 순서는 선언 순이다`() {
        assertEquals(listOf(NotificationCategory.MESSAGE to true, NotificationCategory.SPOT_QA to true), NotificationPreference.effective(emptyList()))
    }

    @Test
    fun `저장된 카테고리만 그 값이고 나머지는 켜짐이다`() {
        val stored = listOf(NotificationPreference(id = 3, memberId = 7, category = NotificationCategory.SPOT_QA, enabled = false))

        assertEquals(listOf(NotificationCategory.MESSAGE to true, NotificationCategory.SPOT_QA to false), NotificationPreference.effective(stored))
    }

    @Test
    fun `변경은 새 값을 돌려주고 원본은 그대로다`() {
        val stored = NotificationPreference(id = 3, memberId = 7, category = NotificationCategory.MESSAGE, enabled = true)

        val changed = stored.change(false)

        assertEquals(false, changed.enabled)
        assertEquals(3L, changed.id)
        assertEquals(true, stored.enabled)
    }

    @Test
    fun `memberId가 양수가 아니면 만들 수 없다`() {
        val e = assertFailsWith<IllegalArgumentException> { NotificationPreference.create(0, NotificationCategory.MESSAGE, true) }
        assertEquals("memberId는 양수여야 한다.", e.message)
    }
}
