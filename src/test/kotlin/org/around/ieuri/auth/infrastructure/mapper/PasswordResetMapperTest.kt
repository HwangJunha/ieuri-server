package org.around.ieuri.auth.infrastructure.mapper

import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PasswordResetEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PasswordResetMapper 필드별 매핑 검증 — 순수 단위테스트(Spring/DB 없음). */
class PasswordResetMapperTest {

    private val publicId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val expiresAt: Instant = Instant.parse("2026-07-01T12:00:00Z")
    private val createdAt: Instant = Instant.parse("2026-07-01T11:00:00Z")

    private fun entity(id: Long? = 21L) = PasswordResetEntity(
        id = id,
        publicId = publicId,
        memberId = 99L,
        codeHash = "hash-xyz",
        expiresAt = expiresAt,
        attempts = 3,
        maxAttempts = 5,
        consumed = false,
        createdAt = createdAt,
    )

    private fun domain(): PasswordReset = PasswordReset.reconstitute(
        id = 21L,
        publicId = publicId,
        memberId = 99L,
        codeHash = "hash-xyz",
        expiresAt = expiresAt,
        attempts = 3,
        maxAttempts = 5,
        consumed = false,
        createdAt = createdAt,
    )

    @Test
    fun `모든 필드가 채워진 엔티티를 도메인으로 옮긴다`() {
        val result = entity().toDomain()

        assertEquals(21L, result.id)
        assertEquals(publicId, result.publicId)
        assertEquals(99L, result.memberId)
        assertEquals("hash-xyz", result.codeHash)
        assertEquals(expiresAt, result.expiresAt)
        assertEquals(3, result.attempts)
        assertEquals(5, result.maxAttempts)
        assertEquals(false, result.consumed)
        assertEquals(createdAt, result.createdAt)
    }

    @Test
    fun `nullable 필드인 id가 null이어도 예외 없이 옮긴다`() {
        val result = entity(id = null).toDomain()

        assertNull(result.id)
        assertEquals(publicId, result.publicId)
        assertEquals(createdAt, result.createdAt)
    }

    @Test
    fun `도메인 모델을 새 엔티티로 옮긴다`() {
        val result = domain().toNewEntity()

        assertNull(result.id) // id는 DB가 채운다.
        assertEquals(publicId, result.publicId)
        assertEquals(99L, result.memberId)
        assertEquals("hash-xyz", result.codeHash)
        assertEquals(expiresAt, result.expiresAt)
        assertEquals(3, result.attempts)
        assertEquals(5, result.maxAttempts)
        assertEquals(false, result.consumed)
    }
}
