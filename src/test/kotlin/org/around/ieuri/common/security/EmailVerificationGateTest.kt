package org.around.ieuri.common.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 미인증 게이트 판정(SPEC-0024 R9). 순수 함수라 Spring 없이 시험한다.
 *
 * 전송 계층이 실제로 최상위 오류를 내는지는 여기서 다루지 않는다 — 인프라에서만 관찰된다.
 */
class EmailVerificationGateTest {

    /** R9-2. 허용 목록 일곱 개는 미인증 상태에서도 각각 통과한다. */
    @Test
    fun `허용 목록의 오퍼레이션은 미인증이어도 통과한다`() {
        val allowed = listOf(
            "me",
            "verifyEmail",
            "resendVerification",
            "retargetEmailVerification",
            "withdrawMember",
            "refreshToken",
            "logout",
        )
        // 스펙 R9가 못박은 일곱 개다 — 이 목록이 조용히 줄어들면 여기서 걸린다.
        assertTrue(allowed.size == 7)
        allowed.forEach { operation ->
            assertTrue(
                EmailVerificationGate.isAllowed(listOf(operation), emailVerified = false),
                "$operation 은(는) 허용 목록이라 통과해야 한다",
            )
        }
        assertTrue(EmailVerificationGate.ALLOWED_OPERATIONS.containsAll(allowed))
    }

    /**
     * 인증 이전 단계 오퍼레이션도 통과한다. 토큰의 주체를 읽지 않기 때문이다 —
     * 미인증 회원의 토큰이 남은 채 다른 계정으로 로그인하려는 경로를 막지 않는다.
     */
    @Test
    fun `인증 이전 단계 오퍼레이션은 미인증이어도 통과한다`() {
        listOf("loginEmail", "loginSns", "registerEmailMember", "registerSnsMember").forEach { operation ->
            assertTrue(
                EmailVerificationGate.isAllowed(listOf(operation), emailVerified = false),
                "$operation 은(는) 인증 이전 단계라 통과해야 한다",
            )
        }
    }

    /** R9-1. */
    @Test
    fun `허용 목록에 없는 오퍼레이션은 미인증이면 거부한다`() {
        assertFalse(EmailVerificationGate.isAllowed(listOf("posts"), emailVerified = false))
        assertFalse(EmailVerificationGate.isAllowed(listOf("createPost"), emailVerified = false))
    }

    /** R9-5. 목록에 없는 **새** 이름이 조용히 통과하지 않는다 — 화이트리스트인 이유다. */
    @Test
    fun `목록에 없는 새 오퍼레이션 이름은 거부한다`() {
        assertFalse(EmailVerificationGate.isAllowed(listOf("someBrandNewMutation"), emailVerified = false))
    }

    /** R9-3. */
    @Test
    fun `인증을 마쳤으면 임의의 오퍼레이션이 통과한다`() {
        assertTrue(EmailVerificationGate.isAllowed(listOf("createPost"), emailVerified = true))
        assertTrue(EmailVerificationGate.isAllowed(listOf("someBrandNewMutation"), emailVerified = true))
    }

    /**
     * 한 요청에 허용·비허용을 섞어 보내 비허용을 통과시키는 길을 막는다.
     * 전부 허용일 때만 통과한다.
     */
    @Test
    fun `허용과 비허용을 섞으면 거부한다`() {
        assertFalse(EmailVerificationGate.isAllowed(listOf("me", "createPost"), emailVerified = false))
        assertTrue(EmailVerificationGate.isAllowed(listOf("me", "logout"), emailVerified = false))
    }
}
