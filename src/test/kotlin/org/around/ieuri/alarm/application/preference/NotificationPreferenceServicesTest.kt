package org.around.ieuri.alarm.application.preference

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.alarm.application.preference.provided.dto.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceQueryPort
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceRepository
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 카테고리별 수신 설정 조회·변경·파기(SPEC-0028 R3·R10·R19). */
class NotificationPreferenceServicesTest {

    private val repository = mockk<NotificationPreferenceRepository>(relaxed = true)
    private val queryPort = mockk<NotificationPreferenceQueryPort>()
    private val query = NotificationPreferenceQueryService(queryPort)
    private val command = NotificationPreferenceModifyService(repository)

    private val memberId = 7L

    private fun preference(category: NotificationCategory, enabled: Boolean) = NotificationPreference(1L, memberId, category, enabled)

    /** AC19.1 — 설정을 바꾼 적 없는 회원도 두 항목이 켜짐으로 온다. */
    @Test
    fun `저장된 행이 없으면 두 카테고리가 모두 켜짐이다`() {
        every { queryPort.findByMemberId(memberId) } returns emptyList()

        val result = query.findByMemberId(memberId)

        assertEquals(NotificationCategory.entries, result.map { it.category })
        assertTrue(result.all { it.enabled })
    }

    /** AC19.2 — 하나만 끈 회원. */
    @Test
    fun `끈 카테고리만 거짓으로 온다`() {
        every { queryPort.findByMemberId(memberId) } returns listOf(preference(NotificationCategory.MESSAGE, false))

        val result = query.findByMemberId(memberId)

        assertEquals(false, result.first { it.category == NotificationCategory.MESSAGE }.enabled)
        assertEquals(true, result.first { it.category == NotificationCategory.SPOT_QA }.enabled)
    }

    /** AC10.3 — 설정을 바꾼 적 없는 회원은 켜짐이다. */
    @Test
    fun `발송 판정은 설정 행이 없으면 켜짐, 있으면 그 값이다`() {
        every { queryPort.findEnabled(memberId, NotificationCategory.MESSAGE) } returns null
        assertTrue(query.isEnabled(memberId, NotificationCategory.MESSAGE))

        every { queryPort.findEnabled(memberId, NotificationCategory.MESSAGE) } returns false
        assertFalse(query.isEnabled(memberId, NotificationCategory.MESSAGE))
    }

    /** 행이 없던 카테고리를 끄면 id 없이 저장한다(새 행). */
    @Test
    fun `행이 없으면 새로 저장한다`() {
        every { repository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE) } returns null
        every { repository.findByMemberId(memberId) } returns emptyList()
        val saved = slot<NotificationPreference>()

        command.update(UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false), memberId)

        verify { repository.save(capture(saved)) }
        assertNull(saved.captured.id)
        assertEquals(memberId, saved.captured.memberId)
        assertEquals(NotificationCategory.MESSAGE, saved.captured.category)
        assertEquals(false, saved.captured.enabled)
    }

    /** 행이 있으면 같은 행(id)을 새 값으로 저장한다. */
    @Test
    fun `행이 있으면 같은 행을 갱신한다`() {
        val existing = preference(NotificationCategory.MESSAGE, true)
        every { repository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE) } returns existing
        every { repository.findByMemberId(memberId) } returns listOf(existing)
        val saved = slot<NotificationPreference>()

        command.update(UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false), memberId)

        verify(exactly = 1) { repository.save(capture(saved)) }
        assertEquals(1L, saved.captured.id)
        assertEquals(false, saved.captured.enabled)
    }

    /** AC19.3 — 응답에 두 카테고리의 최종 상태가 모두 담긴다(저장 뒤 다시 읽은 값). */
    @Test
    fun `변경 응답에 두 카테고리가 모두 담긴다`() {
        every { repository.findByMemberIdAndCategory(any(), any()) } returns null
        every { repository.findByMemberId(memberId) } returns listOf(preference(NotificationCategory.MESSAGE, false))

        val result = command.update(UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false), memberId)

        assertEquals(NotificationCategory.entries, result.map { it.category })
        assertEquals(false, result.first { it.category == NotificationCategory.MESSAGE }.enabled)
        assertEquals(true, result.first { it.category == NotificationCategory.SPOT_QA }.enabled)
    }

    /** R3·AC3.1 */
    @Test
    fun `탈퇴하면 회원의 수신 설정이 지워진다`() {
        command.eraseMemberPreferences(7L)

        verify { repository.deleteByMemberId(7L) }
    }
}
