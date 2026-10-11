package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberQueryPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 회원 조회. 약관 동의 상태(SPEC-0011 R9·R11)와 부재 응답, 목록 크기 상한이 서비스의 판단이다. */
class MemberQueryServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-08-07T00:00:00Z")
    private val agreedAt = Instant.parse("2026-08-01T00:00:00Z")

    private val memberQueryPort = mockk<MemberQueryPort>()
    private val memberRepository = mockk<MemberRepository>()
    private val service = MemberQueryService(memberQueryPort, memberRepository, Clock.fixed(now, ZoneOffset.UTC))

    private val serviceTerms = Terms(id = 1, code = "SERVICE", version = "1.0", required = true)
    private val marketingTerms = Terms(id = 4, code = "MARKETING", version = "1.3", required = false)

    private fun givenTerms(vararg terms: Terms) {
        every { memberRepository.findEffectiveTerms(now) } returns terms.toList()
    }

    private fun givenAgreements(vararg agreements: Agreement) {
        every { memberRepository.findAgreements(memberId) } returns agreements.toList()
    }

    @Test
    fun `동의 상태는 시행 중인 약관 전부를 필수 여부와 버전과 함께 담는다`() {
        givenTerms(serviceTerms, marketingTerms)
        givenAgreements(Agreement(1, "SERVICE", "1.0", agreed = true, agreedAt = agreedAt))

        val statuses = service.agreements(memberId)

        assertEquals(
            listOf(
                AgreementStatusView("SERVICE", required = true, agreed = true, agreedAt = agreedAt, version = "1.0"),
                // 동의한 적 없는 약관은 거짓이고 시각이 없다.
                AgreementStatusView("MARKETING", required = false, agreed = false, agreedAt = null, version = "1.3"),
            ),
            statuses,
        )
    }

    @Test
    fun `철회한 약관은 거짓이고 시각이 null이다`() {
        givenTerms(marketingTerms)
        givenAgreements(Agreement(4, "MARKETING", "1.3", agreed = false, agreedAt = agreedAt))

        val status = service.agreements(memberId).single()

        assertEquals(false, status.agreed)
        assertNull(status.agreedAt)
    }

    /** R11. 시행 중인 것은 2.0인데 회원의 동의는 1.0에만 있다. */
    @Test
    fun `새 버전이 시행되면 이전 버전의 동의는 이월되지 않는다`() {
        givenTerms(serviceTerms, Terms(id = 9, code = "SERVICE", version = "2.0", required = true))
        givenAgreements(Agreement(1, "SERVICE", "1.0", agreed = true, agreedAt = agreedAt))

        val status = service.agreements(memberId).single()

        assertEquals(AgreementStatusView("SERVICE", required = true, agreed = false, agreedAt = null, version = "2.0"), status)
    }

    @Test
    fun `없는 회원의 본인 조회와 단건 조회는 회원 없음으로 답한다`() {
        every { memberQueryPort.findMe(memberId) } returns null
        every { memberQueryPort.findMemberDetail(memberId) } returns null

        val me = assertFailsWith<MemberNotFoundException> { service.me(memberId) }
        val detail = assertFailsWith<MemberNotFoundException> { service.member(memberId) }

        assertEquals("회원을 찾을 수 없습니다.", me.message)
        assertEquals("회원을 찾을 수 없습니다.", detail.message)
    }

    // --- 회원 목록 ---

    private val emptyPage = CursorPage<MemberSummaryView>(
        edges = emptyList(),
        pageInfo = CursorPageInfo(hasNextPage = false, hasPreviousPage = false, startCursor = null, endCursor = null),
        totalCount = 0L,
    )

    /** 상한을 넘으면 오류가 아니라 잘라낸다(POL-018). 어댑터는 받은 값을 그대로 쓴다. */
    @Test
    fun `회원 목록의 first는 1에서 100 사이로 맞춰 포트에 넘긴다`() {
        val asked = mutableListOf<MembersQuery>()
        every { memberQueryPort.findMembers(capture(asked)) } returns emptyPage

        listOf(-5, 0, 1, 37, 100, 101, Int.MAX_VALUE).forEach { first ->
            service.members(MembersQuery(status = null, roleCode = null, first = first, after = null))
        }

        assertEquals(listOf(1, 1, 1, 37, 100, 100, 100), asked.map { it.first })
    }

    @Test
    fun `회원 목록의 나머지 조건은 손대지 않고 넘기고 포트가 준 페이지를 그대로 돌려준다`() {
        val asked = mutableListOf<MembersQuery>()
        every { memberQueryPort.findMembers(capture(asked)) } returns emptyPage

        val filtered = service.members(MembersQuery(status = MemberStatus.SUSPENDED, roleCode = "ADMIN", first = 500, after = "커서-값"))
        service.members(MembersQuery(status = null, roleCode = null, first = 20, after = null))

        assertSame(emptyPage, filtered)
        assertEquals(MemberStatus.SUSPENDED, asked[0].status)
        assertEquals("ADMIN", asked[0].roleCode)
        assertEquals(100, asked[0].first)
        assertEquals("커서-값", asked[0].after)
        // 필터와 커서가 없으면 없는 채로 넘긴다.
        assertNull(asked[1].status)
        assertNull(asked[1].roleCode)
        assertEquals(20, asked[1].first)
        assertNull(asked[1].after)
    }

    /** 다른 context가 존재 확인 용도로도 쓰므로 부재는 예외가 아니다. */
    @Test
    fun `없는 회원의 공개 프로필은 예외가 아니라 null이다`() {
        every { memberQueryPort.findPublicProfile(memberId) } returns null

        assertNull(service.memberPublicProfile(memberId))
    }
}
