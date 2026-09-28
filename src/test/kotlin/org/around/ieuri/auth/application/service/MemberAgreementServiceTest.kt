package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.command.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.command.UpdateAgreementCommand
import org.around.ieuri.auth.application.exception.TermsCodeNotRequiredException
import org.around.ieuri.auth.application.exception.TermsNotFoundException
import org.around.ieuri.auth.application.port.out.persistence.AgreementRecord
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberAgreementRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsKey
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.domain.vo.Agreement
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 약관 동의 조회·변경(SPEC-0011 R9~R12). 버전 상승 시 이월 없음, 이력(감사 로그)과 상태(동의 테이블)의 분리를 고정한다. */
class MemberAgreementServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-08-07T00:00:00Z")
    private val agreedAt = Instant.parse("2026-08-01T00:00:00Z")

    private val termsRepository = mockk<TermsRepositoryPort>()
    private val agreementRepository = mockk<MemberAgreementRepositoryPort>(relaxed = true)
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val service = MemberAgreementService(
        termsRepository,
        agreementRepository,
        auditLog,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val serviceV1 = TermsView(id = 1, code = "SERVICE", version = "1.0", required = true)
    private val privacyV1 = TermsView(id = 2, code = "PRIVACY", version = "1.0", required = true)
    private val locationV1 = TermsView(id = 3, code = "LOCATION", version = "1.0", required = false)
    private val marketingV1 = TermsView(id = 4, code = "MARKETING", version = "1.0", required = false)

    private val allTerms = listOf(serviceV1, privacyV1, locationV1, marketingV1)

    private fun givenTerms(terms: List<TermsView> = allTerms) {
        every { termsRepository.findActiveTerms(now) } returns terms
    }

    private fun givenHistory(vararg entries: Pair<TermsKey, AgreementRecord>) {
        every { agreementRepository.findCurrentAgreements(memberId) } returns entries.toMap()
    }

    // --- R9 상태 조회 ---

    @Test
    fun `전부 동의한 회원은 네 건 모두 참이다`() {
        givenTerms()
        givenHistory(
            *allTerms.map {
                TermsKey(it.code, it.version) to AgreementRecord(true, agreedAt)
            }.toTypedArray(),
        )

        val statuses = service.agreements(memberId)

        assertEquals(4, statuses.size)
        assertTrue(statuses.all { it.agreed })
        assertTrue(statuses.all { it.agreedAt == agreedAt })
    }

    @Test
    fun `동의 이력이 없으면 거짓이고 시각은 null이다`() {
        givenTerms()
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(true, agreedAt))

        val marketing = service.agreements(memberId).first { it.termsCode == "MARKETING" }

        assertFalse(marketing.agreed)
        assertNull(marketing.agreedAt)
    }

    @Test
    fun `철회 이력이 있으면 거짓이고 시각은 null이다`() {
        givenTerms()
        givenHistory(TermsKey("MARKETING", "1.0") to AgreementRecord(false, agreedAt))

        val marketing = service.agreements(memberId).first { it.termsCode == "MARKETING" }

        assertFalse(marketing.agreed)
        assertNull(marketing.agreedAt)
    }

    @Test
    fun `required와 version이 시행 중인 약관의 값과 일치한다`() {
        givenTerms()
        givenHistory()

        val statuses = service.agreements(memberId).associateBy { it.termsCode }

        assertTrue(statuses.getValue("SERVICE").required)
        assertTrue(statuses.getValue("PRIVACY").required)
        assertFalse(statuses.getValue("LOCATION").required)
        assertFalse(statuses.getValue("MARKETING").required)
        assertTrue(statuses.values.all { it.version == "1.0" })
    }

    // --- R11 버전 상승 ---

    @Test
    fun `새 버전이 시행되면 이전 버전의 동의는 이월되지 않는다`() {
        // 시행 중인 것은 2.0인데 회원의 동의는 1.0에만 있다.
        givenTerms(listOf(serviceV1.copy(version = "2.0")))
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(true, agreedAt))

        val status = service.agreements(memberId).single()

        assertEquals("2.0", status.version)
        assertFalse(status.agreed, "이전 버전 동의가 이월됐다")
        assertNull(status.agreedAt)
    }

    // --- R10 선택 약관 토글 ---

    @Test
    fun `선택 약관을 끄면 거짓 행을 추가한다`() {
        givenTerms()
        givenHistory(TermsKey("MARKETING", "1.0") to AgreementRecord(false, now))
        val appended = slot<List<Agreement>>()
        every { agreementRepository.upsert(memberId, capture(appended)) } returns Unit

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = false))

        assertFalse(status.agreed)
        val row = appended.captured.single()
        assertEquals("MARKETING", row.termsCode)
        assertFalse(row.agreed)
        assertEquals(now, row.agreedAt)
    }

    @Test
    fun `선택 약관을 켜면 참 행을 추가하고 시각이 갱신된다`() {
        givenTerms()
        givenHistory(TermsKey("LOCATION", "1.0") to AgreementRecord(true, now))

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "LOCATION", agreed = true))

        assertTrue(status.agreed)
        assertEquals(now, status.agreedAt)
    }

    @Test
    fun `필수 약관도 철회할 수 있다`() {
        // 필수 약관도 철회할 수 있다.
        givenTerms()
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(false, now))
        val appended = slot<List<Agreement>>()
        every { agreementRepository.upsert(memberId, capture(appended)) } returns Unit

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "SERVICE", agreed = false))

        assertFalse(status.agreed)
        // 필수 약관이라는 사실은 그대로다 — 클라이언트는 이 조합으로 게이트를 건다.
        assertTrue(status.required)
        assertFalse(appended.captured.single().agreed)
    }

    @Test
    fun `철회한 필수 약관은 다시 동의할 수 있다`() {
        givenTerms()
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(true, now))

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "SERVICE", agreed = true))

        assertTrue(status.agreed)
    }

    // --- 감사 로그 (동의 이력 보존) ---

    @Test
    fun `동의와 철회를 감사 로그에 남긴다`() {
        // 동의 테이블은 탈퇴 시 함께 지워지므로 감사 로그가 최종 보관처다.
        givenTerms()
        givenHistory()

        service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = true))
        verify { auditLog.record(memberId, "TERMS_AGREED", any()) }

        service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = false))
        verify { auditLog.record(memberId, "TERMS_WITHDRAWN", any()) }
    }

    @Test
    fun `감사 로그에 약관 식별자만 담고 본문은 담지 않는다`() {
        givenTerms()
        givenHistory()
        val payload = slot<Map<String, Any?>>()
        every { auditLog.record(memberId, any(), capture(payload)) } returns Unit

        service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = true))

        val recorded = payload.captured
        assertEquals(true, recorded["agreed"])
        @Suppress("UNCHECKED_CAST")
        val terms = recorded["terms"] as List<Map<String, Any?>>
        assertEquals("MARKETING", terms.single()["code"])
        assertEquals("1.0", terms.single()["version"])
    }

    @Test
    fun `시행 중이지 않은 코드는 거절한다`() {
        givenTerms(listOf(serviceV1))

        assertFailsWith<TermsNotFoundException> {
            service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = true))
        }
    }

    // --- R12 필수 약관 동의 ---

    @Test
    fun `필수 약관 두 건을 모두 제출하면 둘 다 참이 된다`() {
        givenTerms()
        givenHistory(
            TermsKey("SERVICE", "1.0") to AgreementRecord(true, now),
            TermsKey("PRIVACY", "1.0") to AgreementRecord(true, now),
        )
        val appended = slot<List<Agreement>>()
        every { agreementRepository.upsert(memberId, capture(appended)) } returns Unit

        val statuses = service.agreeToRequiredTerms(
            AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "PRIVACY")),
        )

        assertEquals(2, appended.captured.size)
        assertTrue(appended.captured.all { it.agreed })
        assertTrue(statuses.filter { it.required }.all { it.agreed })
    }

    @Test
    fun `한 건만 제출하면 나머지는 거짓으로 남는다`() {
        givenTerms()
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(true, now))

        val statuses = service.agreeToRequiredTerms(
            AgreeToRequiredTermsCommand(memberId, listOf("SERVICE")),
        ).associateBy { it.termsCode }

        assertTrue(statuses.getValue("SERVICE").agreed)
        assertFalse(statuses.getValue("PRIVACY").agreed)
    }

    @Test
    fun `선택 약관 코드가 섞이면 전부 거절한다`() {
        givenTerms()

        assertFailsWith<TermsCodeNotRequiredException> {
            service.agreeToRequiredTerms(
                AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "MARKETING")),
            )
        }
        verify(exactly = 0) { agreementRepository.upsert(any(), any()) }
    }

    @Test
    fun `시행 중이지 않은 코드가 섞이면 전부 거절한다`() {
        givenTerms()

        assertFailsWith<TermsNotFoundException> {
            service.agreeToRequiredTerms(
                AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "UNKNOWN")),
            )
        }
        verify(exactly = 0) { agreementRepository.upsert(any(), any()) }
    }

    @Test
    fun `중복 제출한 코드는 한 번만 기록한다`() {
        givenTerms()
        givenHistory(TermsKey("SERVICE", "1.0") to AgreementRecord(true, now))
        val appended = slot<List<Agreement>>()
        every { agreementRepository.upsert(memberId, capture(appended)) } returns Unit

        service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "SERVICE")))

        assertEquals(1, appended.captured.size)
    }
}
