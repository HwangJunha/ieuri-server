package org.around.ieuri.auth.infrastructure.mapper

import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** EmailVerificationMapper 필드별 매핑 검증 — 순수 단위테스트(Spring/DB 없음). */
class EmailVerificationMapperTest {

    private val publicId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val expiresAt: Instant = Instant.parse("2026-06-01T10:00:00Z")
    private val verifiedAt: Instant = Instant.parse("2026-06-01T09:30:00Z")
    private val createdAt: Instant = Instant.parse("2026-06-01T09:00:00Z")

    private fun entity(
        id: Long? = 13L,
        verifiedAt: Instant? = this.verifiedAt,
    ) = EmailVerificationEntity(
        id = id,
        publicId = publicId,
        memberId = 77L,
        codeHash = "hash-abc",
        expiresAt = expiresAt,
        attempts = 2,
        maxAttempts = 5,
        consumed = true,
        verifiedAt = verifiedAt,
        createdAt = createdAt,
        purpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmailEncrypted = "enc:new@example.com",
    )

    private fun domain(verifiedAt: Instant? = this.verifiedAt): EmailVerification = EmailVerification.reconstitute(
        id = 13L,
        publicId = publicId,
        memberId = 77L,
        codeHash = "hash-abc",
        expiresAt = expiresAt,
        attempts = 2,
        maxAttempts = 5,
        consumed = true,
        verifiedAt = verifiedAt,
        createdAt = createdAt,
        purpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmailEncrypted = "enc:new@example.com",
    )

    @Test
    fun `모든 필드가 채워진 엔티티를 도메인으로 옮긴다`() {
        val result = entity().toDomain()

        assertEquals(13L, result.id)
        assertEquals(publicId, result.publicId)
        assertEquals(77L, result.memberId)
        assertEquals("hash-abc", result.codeHash)
        assertEquals(expiresAt, result.expiresAt)
        assertEquals(2, result.attempts)
        assertEquals(5, result.maxAttempts)
        assertEquals(true, result.consumed)
        assertEquals(verifiedAt, result.verifiedAt)
        assertEquals(createdAt, result.createdAt)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, result.purpose)
        assertEquals("enc:new@example.com", result.targetEmailEncrypted)
    }

    @Test
    fun `nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val result = entity(id = null, verifiedAt = null).toDomain()

        assertNull(result.id)
        assertNull(result.verifiedAt)
        // 나머지 필수 필드는 그대로 실린다.
        assertEquals(publicId, result.publicId)
        assertEquals(createdAt, result.createdAt)
    }

    @Test
    fun `도메인 모델을 새 엔티티로 옮긴다`() {
        val result = domain().toNewEntity()

        assertNull(result.id) // id는 DB가 채운다.
        assertEquals(publicId, result.publicId)
        assertEquals(77L, result.memberId)
        assertEquals("hash-abc", result.codeHash)
        assertEquals(expiresAt, result.expiresAt)
        assertEquals(2, result.attempts)
        assertEquals(5, result.maxAttempts)
        assertEquals(true, result.consumed)
        assertEquals(verifiedAt, result.verifiedAt)
    }

    @Test
    fun `검증 전 도메인의 verifiedAt은 null로 옮긴다`() {
        val result = domain(verifiedAt = null).toNewEntity()

        assertNull(result.verifiedAt)
        assertEquals("hash-abc", result.codeHash)
    }
}
