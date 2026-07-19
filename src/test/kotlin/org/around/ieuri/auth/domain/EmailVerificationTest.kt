package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.VerificationFailureReason
import org.around.ieuri.auth.domain.aggregate.VerificationResult
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** EmailVerification.verify 규칙(만료/시도초과/불일치/단회용) — 순수 도메인 단위테스트. */
class EmailVerificationTest {

    private val now: Instant = Instant.parse("2026-07-16T00:00:00Z")
    private val correctHash = "hash-correct"

    private fun issue(maxAttempts: Int = 5): EmailVerification =
        EmailVerification.issue(
            publicId = UUID.randomUUID(),
            memberId = 1L,
            codeHash = correctHash,
            ttl = Duration.ofMinutes(10),
            now = now,
            maxAttempts = maxAttempts,
        )

    @Test
    fun `올바른 코드면 성공하고 단회용으로 소진되며 verifiedAt이 기록된다`() {
        val ev = issue()
        val result = ev.verify(correctHash, now.plusSeconds(60))
        assertTrue(result is VerificationResult.Success)
        assertTrue(ev.consumed)
        assertNotNull(ev.verifiedAt)
    }

    @Test
    fun `성공 후 재검증은 CONSUMED로 실패한다`() {
        val ev = issue()
        ev.verify(correctHash, now.plusSeconds(60))
        val second = ev.verify(correctHash, now.plusSeconds(120))
        assertEquals(VerificationFailureReason.CONSUMED, (second as VerificationResult.Failure).reason)
    }

    @Test
    fun `만료 후에는 EXPIRED로 실패한다`() {
        val ev = issue()
        val result = ev.verify(correctHash, now.plus(Duration.ofMinutes(11)))
        assertEquals(VerificationFailureReason.EXPIRED, (result as VerificationResult.Failure).reason)
        assertNull(ev.verifiedAt)
    }

    @Test
    fun `코드가 틀리면 CODE_MISMATCH로 실패하고 attempts가 증가한다`() {
        val ev = issue()
        val result = ev.verify("hash-wrong", now.plusSeconds(60))
        assertEquals(VerificationFailureReason.CODE_MISMATCH, (result as VerificationResult.Failure).reason)
        assertEquals(1, ev.attempts)
    }

    @Test
    fun `최대 시도를 채우면 소진되고 이후 MAX_ATTEMPTS_EXCEEDED로 실패한다`() {
        val ev = issue(maxAttempts = 3)
        repeat(3) { ev.verify("hash-wrong", now.plusSeconds(60)) }
        assertTrue(ev.consumed)
        assertEquals(3, ev.attempts)

        // 소진된 뒤에는 올바른 코드여도 통과하지 못한다.
        val result = ev.verify(correctHash, now.plusSeconds(70))
        assertTrue(result is VerificationResult.Failure)
    }
}
