package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.ACTIVE_TERMS
import org.around.ieuri.auth.domain.member.MemberFixtures.MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import org.around.ieuri.auth.domain.member.MemberFixtures.role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 역할 부여·회수(자기 강등 방지)와 약관 동의(SPEC-0011 R10·R12). */
class MemberRoleAgreementTest {

    private val admin = role(1L, "ADMIN", Permission.ROLE_ASSIGN, Permission.ROLE_REVOKE)
    private val manager = role(2L, "MANAGER", Permission.ROLE_REVOKE)
    private val user = role(3L, "USER", Permission.MEMBER_READ)
    private val other = 99L

    // ── 역할 ──

    @Test
    fun `갖지 않은 역할은 부여된다`() {
        val change = member().grantRole(admin, held = listOf(user))

        assertEquals(MemberRoleChange.Granted(MEMBER_ID, admin), change)
    }

    @Test
    fun `이미 가진 역할은 다시 부여하지 않는다 - 코드로 판정한다`() {
        val sameCodeOtherId = admin.copy(id = 500L, name = "다른-이름")

        assertEquals(MemberRoleChange.Unchanged, member().grantRole(admin, held = listOf(user, sameCodeOtherId)))
    }

    @Test
    fun `가진 역할은 회수된다`() {
        val change = member().revokeRole(user, held = listOf(user, admin), actorMemberId = other)

        assertEquals(MemberRoleChange.Revoked(MEMBER_ID, user), change)
    }

    @Test
    fun `갖지 않은 역할의 회수는 바꾸지 않는다`() {
        assertEquals(MemberRoleChange.Unchanged, member().revokeRole(admin, held = listOf(user), actorMemberId = other))
    }

    @Test
    fun `본인의 마지막 관리 역할은 스스로 거둘 수 없다`() {
        val e = assertFailsWith<SelfDemotionException> { member().revokeRole(admin, held = listOf(admin, user), actorMemberId = MEMBER_ID) }

        assertEquals("마지막 관리 역할은 스스로 회수할 수 없습니다.", e.message)
        assertEquals("SELF_DEMOTION", e.code)
    }

    @Test
    fun `다른 관리 역할이 남으면 본인도 거둘 수 있다`() {
        val change = member().revokeRole(admin, held = listOf(admin, manager), actorMemberId = MEMBER_ID)

        assertEquals(MemberRoleChange.Revoked(MEMBER_ID, admin), change)
    }

    @Test
    fun `남이 거두면 마지막 관리 역할이어도 회수된다`() {
        val change = member().revokeRole(admin, held = listOf(admin), actorMemberId = other)

        assertEquals(MemberRoleChange.Revoked(MEMBER_ID, admin), change)
    }

    @Test
    fun `관리 역할이 아니면 본인이 거둘 수 있다`() {
        val change = member().revokeRole(user, held = listOf(user), actorMemberId = MEMBER_ID)

        assertEquals(MemberRoleChange.Revoked(MEMBER_ID, user), change)
    }

    /** 가졌는지보다 자기 강등 판정이 먼저다 — 갖지 않은 관리 역할을 스스로 거두려 해도 거부된다. */
    @Test
    fun `자기 강등 판정은 보유 여부보다 먼저다`() {
        assertFailsWith<SelfDemotionException> { member().revokeRole(admin, held = listOf(user), actorMemberId = MEMBER_ID) }
    }

    // ── 약관 ──

    @Test
    fun `약관 동의는 시행 중인 약관의 id와 버전을 담는다`() {
        val agreed = member().decideAgreement("MARKETING", agreed = true, ACTIVE_TERMS, NOW)

        assertEquals(13L, agreed.termsId)
        assertEquals("MARKETING", agreed.termsCode)
        assertEquals("3.0", agreed.termsVersion)
        assertEquals(true, agreed.agreed)
        assertEquals(NOW, agreed.agreedAt)
    }

    @Test
    fun `필수 약관도 철회할 수 있고 철회 시각이 남는다`() {
        val withdrawn = member().decideAgreement("PRIVACY", agreed = false, ACTIVE_TERMS, NOW)

        assertEquals(12L, withdrawn.termsId)
        assertEquals("PRIVACY", withdrawn.termsCode)
        assertEquals("2.0", withdrawn.termsVersion)
        assertEquals(false, withdrawn.agreed)
        assertEquals(NOW, withdrawn.agreedAt)
    }

    @Test
    fun `시행 중이 아닌 약관 코드는 거부한다`() {
        val e = assertFailsWith<TermsNotFoundException> { member().decideAgreement("UNKNOWN", agreed = true, ACTIVE_TERMS, NOW) }

        assertEquals("시행 중인 약관이 아닙니다.", e.message)
        assertEquals("TERMS_NOT_FOUND", e.code)
    }

    @Test
    fun `필수 약관 동의는 제출한 순서대로 전부 동의로 만든다`() {
        val agreements = member().agreeToRequiredTerms(listOf("PRIVACY", "SERVICE"), ACTIVE_TERMS, NOW)

        assertEquals(listOf("PRIVACY", "SERVICE"), agreements.map { it.termsCode })
        assertEquals(listOf(12L, 11L), agreements.map { it.termsId })
        assertEquals(listOf("2.0", "1.0"), agreements.map { it.termsVersion })
        assertEquals(listOf(true, true), agreements.map { it.agreed })
        assertEquals(listOf(NOW, NOW), agreements.map { it.agreedAt })
    }

    @Test
    fun `같은 코드는 한 번만 센다`() {
        val agreements = member().agreeToRequiredTerms(listOf("SERVICE", "PRIVACY", "SERVICE"), ACTIVE_TERMS, NOW)

        assertEquals(listOf("SERVICE", "PRIVACY"), agreements.map { it.termsCode })
    }

    @Test
    fun `선택 약관이 섞이면 전부 거절한다`() {
        val e = assertFailsWith<TermsCodeNotRequiredException> { member().agreeToRequiredTerms(listOf("SERVICE", "MARKETING"), ACTIVE_TERMS, NOW) }

        assertEquals("필수 약관이 아닙니다.", e.message)
        assertEquals("TERMS_CODE_NOT_REQUIRED", e.code)
    }

    @Test
    fun `어긋난 코드가 여럿이면 먼저 제출한 코드의 사유로 답한다`() {
        assertFailsWith<TermsNotFoundException> { member().agreeToRequiredTerms(listOf("UNKNOWN", "MARKETING"), ACTIVE_TERMS, NOW) }
        assertFailsWith<TermsCodeNotRequiredException> { member().agreeToRequiredTerms(listOf("MARKETING", "UNKNOWN"), ACTIVE_TERMS, NOW) }
    }

    @Test
    fun `제출한 코드가 없으면 만들 동의도 없다`() {
        assertEquals(emptyList(), member().agreeToRequiredTerms(emptyList(), ACTIVE_TERMS, NOW))
    }
}
