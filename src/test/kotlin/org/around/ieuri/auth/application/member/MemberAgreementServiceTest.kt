package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.TermsCodeNotRequiredException
import org.around.ieuri.auth.domain.member.TermsNotFoundException
import org.around.ieuri.auth.domain.member.event.TermsAgreementChanged
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 약관 동의 변경(SPEC-0011 R10·R12).
 * - 무엇을 쓸지는 도메인이 정하고, 돌려주는 상태는 쓴 뒤 저장소가 답한 것이다.
 */
class MemberAgreementServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-08-07T00:00:00Z")
    private val storedAt = Instant.parse("2026-08-01T00:00:00Z")

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()
    private val service = MemberAgreementService(memberRepository, eventPublisher, Clock.fixed(now, ZoneOffset.UTC))

    private val serviceTerms = Terms(id = 1, code = "SERVICE", version = "1.0", required = true)
    private val privacyTerms = Terms(id = 2, code = "PRIVACY", version = "1.1", required = true)
    private val marketingTerms = Terms(id = 4, code = "MARKETING", version = "1.3", required = false)

    init {
        every { eventPublisher.publish(capture(events)) } just runs
        every { memberRepository.findById(memberId) } returns memberFixture(id = memberId)
        every { memberRepository.findEffectiveTerms(now) } returns listOf(serviceTerms, privacyTerms, marketingTerms)
        every { memberRepository.findAgreements(memberId) } returns emptyList()
    }

    private fun stored(terms: Terms, agreed: Boolean) = Agreement(terms.id, terms.code, terms.version, agreed, storedAt)

    private fun verifyNothingWritten() {
        verify(exactly = 0) { memberRepository.saveAgreements(any(), any()) }
        assertTrue(events.isEmpty())
    }

    // --- R10 토글 ---

    @Test
    fun `약관을 켜면 시행 중인 버전에 대한 동의를 저장하고 저장소가 답한 상태를 돌려준다`() {
        every { memberRepository.findAgreements(memberId) } returns listOf(stored(marketingTerms, agreed = true))

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "MARKETING", agreed = true))

        verifyOrder {
            memberRepository.saveAgreements(memberId, listOf(Agreement(4, "MARKETING", "1.3", agreed = true, agreedAt = now)))
            memberRepository.findAgreements(memberId)
        }
        // 돌려주는 시각은 방금 만든 값이 아니라 쓴 뒤 읽은 값이다.
        assertEquals(AgreementStatusView("MARKETING", required = false, agreed = true, agreedAt = storedAt, version = "1.3"), status)
        assertEquals(listOf<Any>(TermsAgreementChanged(memberId, listOf(marketingTerms), agreed = true)), events)
    }

    /** 필수 약관이라는 사실은 그대로다 — 클라이언트는 `required=true, agreed=false`로 게이트를 건다(R11). */
    @Test
    fun `필수 약관도 철회할 수 있고 철회로 알린다`() {
        every { memberRepository.findAgreements(memberId) } returns listOf(stored(serviceTerms, agreed = false))

        val status = service.updateAgreement(UpdateAgreementCommand(memberId, "SERVICE", agreed = false))

        verify { memberRepository.saveAgreements(memberId, listOf(Agreement(1, "SERVICE", "1.0", agreed = false, agreedAt = now))) }
        assertEquals(AgreementStatusView("SERVICE", required = true, agreed = false, agreedAt = null, version = "1.0"), status)
        assertEquals(listOf<Any>(TermsAgreementChanged(memberId, listOf(serviceTerms), agreed = false)), events)
    }

    /** 시행 중인 것은 코드마다 가장 큰 버전 하나다. 동의는 그 버전에 대해 남긴다. */
    @Test
    fun `같은 코드에 여러 버전이 시행 중이면 최신 버전에 동의를 남긴다`() {
        val serviceV2 = Terms(id = 9, code = "SERVICE", version = "2.0", required = true)
        every { memberRepository.findEffectiveTerms(now) } returns listOf(serviceTerms, serviceV2)

        service.updateAgreement(UpdateAgreementCommand(memberId, "SERVICE", agreed = true))

        verify { memberRepository.saveAgreements(memberId, listOf(Agreement(9, "SERVICE", "2.0", agreed = true, agreedAt = now))) }
        assertEquals(listOf<Any>(TermsAgreementChanged(memberId, listOf(serviceV2), agreed = true)), events)
    }

    @Test
    fun `시행 중이지 않은 코드의 토글은 아무것도 쓰지 않고 거부한다`() {
        val ex = assertFailsWith<TermsNotFoundException> { service.updateAgreement(UpdateAgreementCommand(memberId, "UNKNOWN", agreed = true)) }

        assertEquals("시행 중인 약관이 아닙니다.", ex.message)
        verifyNothingWritten()
    }

    @Test
    fun `없는 회원의 동의 변경은 회원 없음으로 답한다`() {
        every { memberRepository.findById(memberId) } returns null

        val toggle = assertFailsWith<MemberNotFoundException> { service.updateAgreement(UpdateAgreementCommand(memberId, "SERVICE", agreed = true)) }
        val required = assertFailsWith<MemberNotFoundException> { service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE"))) }

        assertEquals("회원을 찾을 수 없습니다.", toggle.message)
        assertEquals("회원을 찾을 수 없습니다.", required.message)
        verifyNothingWritten()
    }

    // --- R12 필수 약관 동의 ---

    @Test
    fun `필수 약관을 제출하면 한꺼번에 저장하고 전체 상태를 돌려준다`() {
        every { memberRepository.findAgreements(memberId) } returns listOf(stored(serviceTerms, agreed = true), stored(privacyTerms, agreed = true))

        val statuses = service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "PRIVACY")))

        verifyOrder {
            memberRepository.saveAgreements(
                memberId,
                listOf(
                    Agreement(1, "SERVICE", "1.0", agreed = true, agreedAt = now),
                    Agreement(2, "PRIVACY", "1.1", agreed = true, agreedAt = now),
                ),
            )
            memberRepository.findAgreements(memberId)
        }
        assertEquals(
            listOf(
                AgreementStatusView("SERVICE", required = true, agreed = true, agreedAt = storedAt, version = "1.0"),
                AgreementStatusView("PRIVACY", required = true, agreed = true, agreedAt = storedAt, version = "1.1"),
                AgreementStatusView("MARKETING", required = false, agreed = false, agreedAt = null, version = "1.3"),
            ),
            statuses,
        )
        assertEquals(listOf<Any>(TermsAgreementChanged(memberId, listOf(serviceTerms, privacyTerms), agreed = true)), events)
    }

    @Test
    fun `중복 제출한 코드는 한 번만 저장한다`() {
        service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "SERVICE")))

        verify(exactly = 1) { memberRepository.saveAgreements(memberId, listOf(Agreement(1, "SERVICE", "1.0", agreed = true, agreedAt = now))) }
        assertEquals(listOf<Any>(TermsAgreementChanged(memberId, listOf(serviceTerms), agreed = true)), events)
    }

    /** 쓸 것이 없으면 쓰지도 알리지도 않는다. 상태는 그대로 돌려준다. */
    @Test
    fun `빈 목록을 제출하면 아무것도 쓰지 않고 현재 상태만 돌려준다`() {
        every { memberRepository.findAgreements(memberId) } returns listOf(stored(serviceTerms, agreed = true))

        val statuses = service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, emptyList()))

        verifyNothingWritten()
        assertEquals(listOf("SERVICE", "PRIVACY", "MARKETING"), statuses.map { it.termsCode })
        assertEquals(listOf(true, false, false), statuses.map { it.agreed })
    }

    @Test
    fun `선택 약관 코드가 섞이면 전부 거절하고 아무것도 쓰지 않는다`() {
        val ex = assertFailsWith<TermsCodeNotRequiredException> {
            service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "MARKETING")))
        }

        assertEquals("필수 약관이 아닙니다.", ex.message)
        verifyNothingWritten()
    }

    @Test
    fun `시행 중이지 않은 코드가 섞이면 전부 거절하고 아무것도 쓰지 않는다`() {
        val ex = assertFailsWith<TermsNotFoundException> {
            service.agreeToRequiredTerms(AgreeToRequiredTermsCommand(memberId, listOf("SERVICE", "UNKNOWN")))
        }

        assertEquals("시행 중인 약관이 아닙니다.", ex.message)
        verifyNothingWritten()
    }
}
