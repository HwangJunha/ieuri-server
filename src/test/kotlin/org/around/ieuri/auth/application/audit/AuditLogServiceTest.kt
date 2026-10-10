package org.around.ieuri.auth.application.audit

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.audit.required.AuditLogRepository
import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 감사 기록 쓰기와 보관 기간 집행(SPEC-0032 R22, POL-014).
 * - 기록 종류마다 보관 기간이 다르다: 회원 감사 365일, 접속기록 1년, 위치 조회 6개월.
 */
class AuditLogServiceTest {

    // 윤일(2028-02-29)을 낀 시각이라 365일과 1년, 6개월이 서로 다른 기준 시각을 낸다.
    private val now = Instant.parse("2028-08-31T00:00:00Z")

    private val repository = mockk<AuditLogRepository>(relaxUnitFun = true)
    private val service = AuditLogService(repository, Clock.fixed(now, ZoneOffset.UTC))

    // --- 기록 ---

    @Test
    fun `회원 감사 기록은 회원과 사건 종류와 내용을 담아 넣는다`() {
        val payload = mapOf("provider" to "APPLE", "reason" to null)

        service.recordMemberAudit(100L, "SNS_UNLINK_FAILED", payload)

        verify(exactly = 1) { repository.insert(MemberAuditLog(100L, "SNS_UNLINK_FAILED", payload)) }
        verify(exactly = 0) { repository.insert(any<MemberAccessLog>()) }
        verify(exactly = 0) { repository.insert(any<LocationAccessLog>()) }
    }

    @Test
    fun `접속기록은 회원과 수행업무와 접속지를 담아 넣는다`() {
        service.recordAccess(100L, "LOGIN", "203.0.113.7")
        service.recordAccess(200L, "LOGOUT", null)

        verify(exactly = 1) { repository.insert(MemberAccessLog(100L, "LOGIN", "203.0.113.7")) }
        // 접속지를 알 수 없으면 null로 남긴다.
        verify(exactly = 1) { repository.insert(MemberAccessLog(200L, "LOGOUT", null)) }
        verify(exactly = 0) { repository.insert(any<MemberAuditLog>()) }
    }

    @Test
    fun `위치 조회 기록은 회원과 맥락을 담아 넣는다`() {
        val payload = mapOf<String, Any?>("operation" to "nearbySpots")

        service.recordLocationAccess(100L, payload)

        verify(exactly = 1) { repository.insert(LocationAccessLog(100L, payload)) }
        verify(exactly = 0) { repository.insert(any<MemberAuditLog>()) }
    }

    // --- 보관 기간 집행 ---

    @Test
    fun `회원 감사 기록은 365일 전을 기준으로 한 회차를 지우고 지운 수를 돌려준다`() {
        every { repository.deleteMemberAuditBefore(Instant.parse("2027-09-01T00:00:00Z"), 500) } returns 37

        assertEquals(37, service.purgeExpiredBatch(500))

        verify(exactly = 0) { repository.deleteAccessBefore(any(), any()) }
        verify(exactly = 0) { repository.deleteLocationAccessBefore(any(), any()) }
    }

    /** 법정 6개월(위치정보법 제16조). 일수가 아니라 개월로 센다. */
    @Test
    fun `위치 조회 기록은 6개월 전을 기준으로 한 회차를 지운다`() {
        every { repository.deleteLocationAccessBefore(Instant.parse("2028-02-29T00:00:00Z"), 200) } returns 12

        assertEquals(12, service.purgeExpiredLocationAccessBatch(200))

        verify(exactly = 0) { repository.deleteMemberAuditBefore(any(), any()) }
        verify(exactly = 0) { repository.deleteAccessBefore(any(), any()) }
    }

    /** 법정 1년(안전성 확보조치 기준 제8조). 윤일을 끼면 365일과 하루 차이가 난다. */
    @Test
    fun `접속기록은 1년 전을 기준으로 한 회차를 지운다`() {
        every { repository.deleteAccessBefore(Instant.parse("2027-08-31T00:00:00Z"), 100) } returns 5

        assertEquals(5, service.purgeExpiredAccessLogBatch(100))

        verify(exactly = 0) { repository.deleteMemberAuditBefore(any(), any()) }
        verify(exactly = 0) { repository.deleteLocationAccessBefore(any(), any()) }
    }
}
