package org.around.ieuri.common.security

/**
 * 회원의 이메일 인증 완료 여부를 조회한다(SPEC-0024 R9 미인증 게이트). auth가 구현한다.
 * - 토큰 클레임이 아니라 조회로 판정한다 — 인증 직후 기존 토큰으로 바로 통과해야 한다.
 */
interface EmailVerificationStatusPort {
    /** 인증을 마쳤으면 참. 회원이 없으면 거짓(존재하지 않는 주체를 통과시키지 않는다). */
    fun isEmailVerified(memberId: Long): Boolean
}
