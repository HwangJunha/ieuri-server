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
        // 스펙이 못박은 개수다 — 목록이 조용히 늘어나면 여기서 걸린다.
        assertTrue(allowed.size == 7)
        allowed.forEach { operation ->
            assertTrue(
                EmailVerificationGate.isAllowed(listOf(operation), emailVerified = false),
                "$operation 은(는) 허용 목록이라 통과해야 한다",
            )
        }
        assertTrue(EmailVerificationGate.ALLOWED_OPERATIONS == allowed.toSet())
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
