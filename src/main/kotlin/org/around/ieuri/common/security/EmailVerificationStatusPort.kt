package org.around.ieuri.common.security

/**
 * 회원의 이메일 인증 완료 여부를 조회한다. 미인증 게이트(SPEC-0024 R9)가 유일한 사용처다.
 *
 * **공유 커널에 두는 이유**: 게이트는 모든 도메인의 오퍼레이션에 걸리는 횡단 관심사라
 * auth 안에 둘 수 없고, 그렇다고 게이트가 auth의 내부를 직접 알 수도 없다.
 * 구현은 auth가 제공한다 — `CurrentMemberProvider`가 공유 커널에 있고 SecurityContext를
 * auth가 채우는 것과 같은 구조다.
 *
 * **토큰 클레임이 아니라 조회로 판정한다.** 클레임에 실으면 인증을 마쳐도 기존 accessToken이
 * 만료될 때까지(최대 5분, POL-008) 게이트에 계속 막힌다. 인증 직후 메인으로 진입해야 하는
 * 요구(R10-3)와 정면으로 어긋나며, 이를 피하려면 인증 성공 시 토큰을 재발급하는 장치를
 * 따로 만들어야 한다. 조회 한 번이 그 장치보다 싸다.
 */
interface EmailVerificationStatusPort {
    /** 인증을 마쳤으면 참. 회원이 없으면 거짓(존재하지 않는 주체를 통과시키지 않는다). */
    fun isEmailVerified(memberId: Long): Boolean
}
