package org.around.ieuri.auth.adapter.event.mapper

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

/* 회원 이벤트 → 감사 기록 payload. 이메일·토큰·약관 본문은 담지 않는다(POL-013). */

fun MemberRegistered.toAuditPayload(): Map<String, Any?> = mapOf("provider" to provider.name, "emailVerified" to emailVerified)

fun EmailVerified.toAuditPayload(): Map<String, Any?> = mapOf("publicId" to verificationPublicId.toString())

fun EmailVerificationRetargeted.toAuditPayload(): Map<String, Any?> = mapOf("provider" to provider.name)

fun EmailChanged.toAuditPayload(): Map<String, Any?> = mapOf("provider" to provider.name, "loginIdentifierUpdated" to loginIdentifierUpdated)

fun SnsUnlinkFailed.toAuditPayload(): Map<String, Any?> = mapOf("provider" to provider.name, "reason" to reason)

fun MemberWithdrawn.toAuditPayload(): Map<String, Any?> = mapOf("provider" to provider.name)

fun MemberSuspended.toAuditPayload(): Map<String, Any?> = mapOf("reason" to reason, "suspendedUntil" to suspendedUntil?.toString())

fun MemberRoleGranted.toAuditPayload(): Map<String, Any?> = mapOf("roleCode" to roleCode, "actorMemberId" to actorMemberId)

fun MemberRoleRevoked.toAuditPayload(): Map<String, Any?> = mapOf("roleCode" to roleCode, "actorMemberId" to actorMemberId)

fun TermsAgreementChanged.toAuditPayload(): Map<String, Any?> = mapOf(
    "terms" to terms.map { mapOf("code" to it.code, "version" to it.version, "required" to it.required) },
    "agreed" to agreed,
)
