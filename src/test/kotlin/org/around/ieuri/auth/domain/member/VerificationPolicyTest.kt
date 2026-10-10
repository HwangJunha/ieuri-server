package org.around.ieuri.auth.domain.member

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 재발송 쿨다운 판정. 경계(정확히 쿨다운)는 지난 것으로 본다. */
class VerificationPolicyTest {

    private val policy = VerificationPolicy(ttl = Duration.ofMinutes(10), maxAttempts = 5, resendCooldown = Duration.ofSeconds(60))
    private val issuedAt = Instant.parse("2026-10-05T03:00:00Z")

    @Test
    fun `발급한 적이 없으면 쿨다운이 없다`() {
        assertTrue(policy.cooldownPassed(lastIssuedAt = null, now = issuedAt))
    }

    @Test
    fun `정확히 쿨다운이 된 순간부터 지난 것이다`() {
        assertFalse(policy.cooldownPassed(issuedAt, issuedAt.plusSeconds(60).minusMillis(1)))
        assertTrue(policy.cooldownPassed(issuedAt, issuedAt.plusSeconds(60)))
        assertTrue(policy.cooldownPassed(issuedAt, issuedAt.plusSeconds(61)))
    }

    @Test
    fun `발급 직후는 쿨다운 안이다`() {
        assertFalse(policy.cooldownPassed(issuedAt, issuedAt))
    }

    @Test
    fun `쿨다운은 만료 시간이 아니라 쿨다운 값으로 센다`() {
        val swapped = VerificationPolicy(ttl = Duration.ofSeconds(1), maxAttempts = 5, resendCooldown = Duration.ofMinutes(10))

        assertFalse(swapped.cooldownPassed(issuedAt, issuedAt.plusSeconds(60)))
        assertTrue(swapped.cooldownPassed(issuedAt, issuedAt.plusSeconds(600)))
    }
}
