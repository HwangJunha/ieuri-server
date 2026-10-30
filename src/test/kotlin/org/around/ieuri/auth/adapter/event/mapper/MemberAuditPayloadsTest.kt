package org.around.ieuri.auth.adapter.event.mapper

import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.event.EmailChanged
import org.around.ieuri.auth.domain.member.event.EmailVerificationRetargeted
import org.around.ieuri.auth.domain.member.event.EmailVerified
import org.around.ieuri.auth.domain.member.event.MemberRegistered
import org.around.ieuri.auth.domain.member.event.MemberRoleGranted
import org.around.ieuri.auth.domain.member.event.MemberRoleRevoked
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.around.ieuri.auth.domain.member.event.SnsUnlinkFailed
import org.around.ieuri.auth.domain.member.event.TermsAgreementChanged
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 회원 이벤트 → 감사 기록 payload(POL-033). 키 집합도 고정한다 —
 * payload에는 회원 id·시각·이메일·토큰이 들어가지 않는다(POL-013).
 */
class MemberAuditPayloadsTest {

    private val at = Instant.parse("2026-10-05T03:00:00Z")
    private val publicId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")

    @Test
    fun `가입 payload는 provider와 인증 여부만 담는다`() {
        val payload = MemberRegistered(memberId = 1L, provider = Provider.KAKAO, emailVerified = true, verificationPublicId = publicId, occurredAt = at).toAuditPayload()
        val unverified = MemberRegistered(memberId = 2L, provider = Provider.EMAIL, emailVerified = false, verificationPublicId = null, occurredAt = at).toAuditPayload()

        assertEquals(setOf("provider", "emailVerified"), payload.keys)
        assertEquals("KAKAO", payload["provider"])
        assertEquals(true, payload["emailVerified"])
        assertEquals("EMAIL", unverified["provider"])
        assertEquals(false, unverified["emailVerified"])
    }

    @Test
    fun `이메일 인증 payload는 인증 핸들을 문자열로 담는다`() {
        val payload = EmailVerified(memberId = 1L, verificationPublicId = publicId, occurredAt = at).toAuditPayload()

        assertEquals(setOf("publicId"), payload.keys)
        assertEquals("aaaaaaaa-1111-2222-3333-444444444444", payload["publicId"])
    }

    @Test
    fun `주소 정정과 탈퇴 payload는 provider만 담는다`() {
        val retargeted = EmailVerificationRetargeted(memberId = 1L, provider = Provider.GOOGLE).toAuditPayload()
        val withdrawn = MemberWithdrawn(memberId = 2L, provider = Provider.APPLE, occurredAt = at).toAuditPayload()

        assertEquals(setOf("provider"), retargeted.keys)
        assertEquals("GOOGLE", retargeted["provider"])
        assertEquals(setOf("provider"), withdrawn.keys)
        assertEquals("APPLE", withdrawn["provider"])
    }

    @Test
    fun `이메일 변경 payload는 provider와 로그인 식별자 갱신 여부를 담는다`() {
        val payload = EmailChanged(memberId = 1L, provider = Provider.EMAIL, loginIdentifierUpdated = true).toAuditPayload()
        val sns = EmailChanged(memberId = 2L, provider = Provider.KAKAO, loginIdentifierUpdated = false).toAuditPayload()

        assertEquals(setOf("provider", "loginIdentifierUpdated"), payload.keys)
        assertEquals("EMAIL", payload["provider"])
        assertEquals(true, payload["loginIdentifierUpdated"])
        assertEquals("KAKAO", sns["provider"])
        assertEquals(false, sns["loginIdentifierUpdated"])
    }

    @Test
    fun `연결 해제 실패 payload는 provider와 사유를 담는다`() {
        val payload = SnsUnlinkFailed(memberId = 1L, provider = Provider.APPLE, reason = "사유-값").toAuditPayload()

        assertEquals(setOf("provider", "reason"), payload.keys)
        assertEquals("APPLE", payload["provider"])
        assertEquals("사유-값", payload["reason"])
    }

    @Test
    fun `정지 payload는 사유와 만료 시각 문자열을 담는다`() {
        val payload = MemberSuspended(memberId = 1L, reason = "사유-값", suspendedUntil = Instant.parse("2026-11-01T00:00:00Z"), occurredAt = at).toAuditPayload()

        assertEquals(setOf("reason", "suspendedUntil"), payload.keys)
        assertEquals("사유-값", payload["reason"])
        assertEquals("2026-11-01T00:00:00Z", payload["suspendedUntil"])
    }

    @Test
    fun `무기한 정지 payload는 만료 시각 키를 null로 남긴다`() {
        val payload = MemberSuspended(memberId = 1L, reason = "사유-값", suspendedUntil = null, occurredAt = at).toAuditPayload()

        assertTrue("suspendedUntil" in payload)
        assertNull(payload["suspendedUntil"])
        assertEquals("사유-값", payload["reason"])
    }

    @Test
    fun `역할 부여와 회수 payload는 역할 코드와 수행자를 담는다 - 대상 회원 id와 섞이지 않는다`() {
        val granted = MemberRoleGranted(memberId = 1L, roleCode = "ADMIN", actorMemberId = 91L).toAuditPayload()
        val revoked = MemberRoleRevoked(memberId = 2L, roleCode = "USER", actorMemberId = 92L).toAuditPayload()

        assertEquals(setOf("roleCode", "actorMemberId"), granted.keys)
        assertEquals("ADMIN", granted["roleCode"])
        assertEquals(91L, granted["actorMemberId"])
        assertEquals(setOf("roleCode", "actorMemberId"), revoked.keys)
        assertEquals("USER", revoked["roleCode"])
        assertEquals(92L, revoked["actorMemberId"])
    }

    @Test
    fun `약관 동의 변경 payload는 약관 식별자 목록과 동의 여부를 담는다`() {
        val payload = TermsAgreementChanged(
            memberId = 1L,
            terms = listOf(Terms(id = 11L, code = "SERVICE", version = "1.0", required = true), Terms(id = 12L, code = "MARKETING", version = "2.1", required = false)),
            agreed = false,
        ).toAuditPayload()

        assertEquals(setOf("terms", "agreed"), payload.keys)
        assertEquals(false, payload["agreed"])
        assertEquals(
            listOf(
                mapOf("code" to "SERVICE", "version" to "1.0", "required" to true),
                mapOf("code" to "MARKETING", "version" to "2.1", "required" to false),
            ),
            payload["terms"],
        )
    }

    @Test
    fun `약관이 없는 동의 변경 payload는 빈 목록을 담는다`() {
        val payload = TermsAgreementChanged(memberId = 1L, terms = emptyList(), agreed = true).toAuditPayload()

        assertEquals(emptyList<Any>(), payload["terms"])
        assertEquals(true, payload["agreed"])
    }
}
