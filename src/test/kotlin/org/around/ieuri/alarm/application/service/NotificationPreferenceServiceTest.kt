package org.around.ieuri.alarm.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.alarm.application.command.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 카테고리별 수신 설정(SPEC-0028 R19). */
class NotificationPreferenceServiceTest {

    private val repository = mockk<NotificationPreferenceRepositoryPort>(relaxed = true)
    private val service = NotificationPreferenceService(repository)

    private val memberId = 7L

    private fun preference(category: NotificationCategory, enabled: Boolean) = NotificationPreference.restore(1L, memberId, category, enabled)

    /** AC19.1 — 설정을 바꾼 적 없는 회원도 두 항목이 켜짐으로 온다. */
    @Test
    fun `저장된 행이 없으면 두 카테고리가 모두 켜짐이다`() {
        every { repository.findByMemberId(memberId) } returns emptyList()

        val result = service.findByMemberId(memberId)

        assertEquals(NotificationCategory.entries.size, result.size)
        assertTrue(result.all { it.enabled })
    }

    /** AC19.2 — 하나만 끈 회원. */
    @Test
    fun `끈 카테고리만 거짓으로 온다`() {
        every { repository.findByMemberId(memberId) } returns
            listOf(preference(NotificationCategory.MESSAGE, false))

        val result = service.findByMemberId(memberId)

        assertEquals(false, result.first { it.category == NotificationCategory.MESSAGE }.enabled)
        assertEquals(true, result.first { it.category == NotificationCategory.SPOT_QA }.enabled)
    }

    /** 저장된 행이 하나뿐이어도 항상 두 항목을 채워 돌려준다. */
    @Test
    fun `조회는 언제나 모든 카테고리를 돌려준다`() {
        every { repository.findByMemberId(memberId) } returns
            listOf(preference(NotificationCategory.SPOT_QA, false))

        val result = service.findByMemberId(memberId)

        assertEquals(NotificationCategory.entries.toSet(), result.map { it.category }.toSet())
    }

    /** 행이 없던 카테고리를 끄면 새로 저장한다. */
    @Test
    fun `행이 없으면 새로 저장한다`() {
        every {
            repository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE)
        } returns null
        every { repository.findByMemberId(memberId) } returns emptyList()
        val saved = slot<NotificationPreference>()

        service.update(
            UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false),
            memberId,
        )

        verify { repository.save(capture(saved)) }
        assertEquals(NotificationCategory.MESSAGE, saved.captured.category)
        assertEquals(false, saved.captured.enabled)
    }

    /** 행이 있으면 새로 저장하지 않고 갱신한다. */
    @Test
    fun `행이 있으면 갱신한다`() {
        val existing = preference(NotificationCategory.MESSAGE, true)
        every {
            repository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE)
        } returns existing
        every { repository.findByMemberId(memberId) } returns listOf(existing)

        service.update(
            UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false),
            memberId,
        )

        verify(exactly = 0) { repository.save(any()) }
        verify { repository.update(existing) }
        assertEquals(false, existing.enabled)
    }

    /** AC19.5 — 같은 값으로 다시 설정해도 성공하며 상태는 그대로다. */
    @Test
    fun `같은 값으로 다시 설정해도 성공한다`() {
        val existing = preference(NotificationCategory.MESSAGE, false)
        every {
            repository.findByMemberIdAndCategory(memberId, NotificationCategory.MESSAGE)
        } returns existing
        every { repository.findByMemberId(memberId) } returns listOf(existing)

        val result = service.update(
            UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false),
            memberId,
        )

        assertEquals(false, existing.enabled)
        assertEquals(false, result.first { it.category == NotificationCategory.MESSAGE }.enabled)
    }

    /** AC19.3 — 응답에 두 카테고리의 최종 상태가 모두 담긴다. */
    @Test
    fun `변경 응답에 두 카테고리가 모두 담긴다`() {
        every { repository.findByMemberIdAndCategory(any(), any()) } returns null
        every { repository.findByMemberId(memberId) } returns
            listOf(preference(NotificationCategory.MESSAGE, false))

        val result = service.update(
            UpdateNotificationPreferenceCommand(NotificationCategory.MESSAGE, false),
            memberId,
        )

        assertEquals(NotificationCategory.entries.size, result.size)
        assertEquals(false, result.first { it.category == NotificationCategory.MESSAGE }.enabled)
        assertEquals(true, result.first { it.category == NotificationCategory.SPOT_QA }.enabled)
    }
}
