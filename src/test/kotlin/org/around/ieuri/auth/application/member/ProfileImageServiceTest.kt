package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.exception.ImageTooLargeException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.PhotoNotUploadedException
import org.around.ieuri.auth.application.member.provided.exception.UnsupportedImageTypeException
import org.around.ieuri.auth.application.member.required.ProfileImageStoragePort
import org.around.ieuri.auth.domain.member.ObjectKeyNotFoundException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 프로필 이미지(SPEC-0011 R6).
 * - 발급 전에 형식·크기를 보고, 확인 단계는 소유권 → 존재 → 크기 → 매직바이트 → 이동 → 반영 → 이전 정리 순이다.
 * - 스토리지 호출은 DB 단계([MemberCommandUseCase]) 밖에 있다.
 */
class ProfileImageServiceTest {

    private val memberId = 7L
    private val otherMemberId = 99L
    private val now = Instant.parse("2026-08-07T00:00:00Z")
    private val ttl = Duration.ofMinutes(30)
    private val maxBytes = 10_485_760L

    private val members = mockk<MemberCommandUseCase>()
    private val storage = mockk<ProfileImageStoragePort>(relaxUnitFun = true)
    private val policy = ProfileImagePolicy(
        uploadUrlTtl = ttl,
        maxUploadBytes = maxBytes,
        allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp"),
    )
    private val service = ProfileImageService(members, storage, policy, Clock.fixed(now, ZoneOffset.UTC))

    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    private val pendingKey = "pending/profile/$memberId/${UUID.fromString("44444444-4444-4444-4444-444444444444")}"
    private val confirmedKey = pendingKey.replaceFirst("pending/", "images/")
    private val previousKey = "images/profile/$memberId/${UUID.fromString("55555555-5555-5555-5555-555555555555")}"

    // --- 발급 ---

    @Test
    fun `허용 형식과 크기면 회원의 임시 키로 서명한 URL을 발급한다`() {
        val signedKey = slot<String>()
        every { storage.presignPut(capture(signedKey), "image/jpeg", 1_024L, now.plus(ttl)) } returns "https://r2.example/put"

        // 선언한 형식은 공백·대소문자를 정리한 값으로 서명한다.
        val view = service.issueProfileImageUploadUrl(IssueProfileImageUploadUrlCommand("  IMAGE/JPEG ", 1_024L), memberId)

        assertEquals("https://r2.example/put", view.uploadUrl)
        assertEquals(now.plus(ttl), view.expiresAt)
        assertEquals(signedKey.captured, view.objectKey)
        assertTrue(view.objectKey.startsWith("pending/profile/$memberId/"), view.objectKey)
    }

    @Test
    fun `허용하지 않는 형식이면 키를 발급하지 않는다`() {
        val ex = assertFailsWith<UnsupportedImageTypeException> {
            service.issueProfileImageUploadUrl(IssueProfileImageUploadUrlCommand("image/gif", 1_024L), memberId)
        }

        assertEquals("지원하지 않는 이미지 형식입니다.", ex.message)
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `크기가 0 이하이거나 상한을 넘으면 키를 발급하지 않는다`() {
        listOf(0L, -1L, maxBytes + 1).forEach { length ->
            val ex = assertFailsWith<ImageTooLargeException> {
                service.issueProfileImageUploadUrl(IssueProfileImageUploadUrlCommand("image/jpeg", length), memberId)
            }
            assertEquals("이미지 크기가 허용 범위를 벗어났습니다.", ex.message)
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `상한과 같은 크기는 발급한다`() {
        every { storage.presignPut(any(), any(), maxBytes, any()) } returns "https://r2.example/put"

        val view = service.issueProfileImageUploadUrl(IssueProfileImageUploadUrlCommand("image/png", maxBytes), memberId)

        assertEquals("https://r2.example/put", view.uploadUrl)
    }

    // --- 확인 ---

    private val confirm = ConfirmProfileImageUploadCommand(pendingKey)

    private fun givenUploaded(size: Long? = 1_024L, prefix: ByteArray? = jpegMagic) {
        every { storage.findSize(pendingKey) } returns size
        every { storage.readPrefix(pendingKey, 12) } returns prefix
    }

    @Test
    fun `확인은 크기 → 매직바이트 → 이동 → 회원 반영 → 이전 오브젝트 정리 순으로 한다`() {
        givenUploaded()
        every { members.attachProfileImage(memberId, pendingKey) } returns previousKey

        val result = service.confirmProfileImageUpload(confirm, memberId)

        assertEquals(confirmedKey, result)
        verifyOrder {
            storage.findSize(pendingKey)
            storage.readPrefix(pendingKey, 12)
            storage.move(sourceKey = pendingKey, targetKey = confirmedKey)
            members.attachProfileImage(memberId, pendingKey)
            storage.delete(previousKey)
        }
    }

    /** 저장소를 먼저 물으면 타인 키의 존재가 응답 시간으로 샌다. */
    @Test
    fun `남의 키와 형식이 어긋난 키는 스토리지를 한 번도 부르지 않고 거부한다`() {
        val rejected = listOf(
            "pending/profile/$otherMemberId/${UUID.fromString("66666666-6666-6666-6666-666666666666")}",
            "pending/community/$memberId/${UUID.fromString("66666666-6666-6666-6666-666666666666")}",
            confirmedKey,
            "not-a-key",
        )

        rejected.forEach { raw ->
            val ex = assertFailsWith<ObjectKeyNotFoundException> { service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(raw), memberId) }
            assertEquals("발급된 적 없는 오브젝트 키입니다.", ex.message)
        }
        verify(exactly = 0) { storage.findSize(any()) }
        verify(exactly = 0) { storage.readPrefix(any(), any()) }
        verify(exactly = 0) { storage.move(any(), any()) }
        verify(exactly = 0) { storage.delete(any()) }
        verify(exactly = 0) { members.attachProfileImage(any(), any()) }
    }

    @Test
    fun `업로드되지 않은 키는 미업로드로 실패하고 옮기지 않는다`() {
        givenUploaded(size = null)

        val ex = assertFailsWith<PhotoNotUploadedException> { service.confirmProfileImageUpload(confirm, memberId) }

        assertEquals("업로드가 확인되지 않았습니다.", ex.message)
        verify(exactly = 0) { storage.readPrefix(any(), any()) }
        verify(exactly = 0) { storage.move(any(), any()) }
        verify(exactly = 0) { members.attachProfileImage(any(), any()) }
    }

    /** 크기 판정이 매직바이트보다 먼저다 — 넘치는 오브젝트는 읽지도 않는다. */
    @Test
    fun `실제 크기가 범위를 벗어나면 앞부분을 읽지 않고 거부한다`() {
        listOf(0L, maxBytes + 1).forEach { size ->
            givenUploaded(size = size)
            val ex = assertFailsWith<ImageTooLargeException> { service.confirmProfileImageUpload(confirm, memberId) }
            assertEquals("이미지 크기가 허용 범위를 벗어났습니다.", ex.message)
        }
        verify(exactly = 0) { storage.readPrefix(any(), any()) }
        verify(exactly = 0) { storage.move(any(), any()) }
    }

    @Test
    fun `매직바이트가 지원 형식이 아니면 옮기지 않고 거부한다`() {
        givenUploaded(prefix = byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val ex = assertFailsWith<UnsupportedImageTypeException> { service.confirmProfileImageUpload(confirm, memberId) }

        assertEquals("지원하지 않는 이미지 형식입니다.", ex.message)
        verify(exactly = 0) { storage.move(any(), any()) }
        verify(exactly = 0) { members.attachProfileImage(any(), any()) }
    }

    @Test
    fun `크기 확인 뒤 오브젝트가 사라졌으면 미업로드로 실패한다`() {
        givenUploaded(prefix = null)

        val ex = assertFailsWith<PhotoNotUploadedException> { service.confirmProfileImageUpload(confirm, memberId) }

        assertEquals("업로드가 확인되지 않았습니다.", ex.message)
        verify(exactly = 0) { storage.move(any(), any()) }
    }

    @Test
    fun `이전 이미지가 없었거나 같은 키면 아무것도 지우지 않는다`() {
        givenUploaded()
        every { members.attachProfileImage(memberId, pendingKey) } returnsMany listOf(null, confirmedKey)

        service.confirmProfileImageUpload(confirm, memberId)
        service.confirmProfileImageUpload(confirm, memberId)

        verify(exactly = 0) { storage.delete(any()) }
    }

    /** 남은 오브젝트는 스토리지 lifecycle rule이 걷어 간다. */
    @Test
    fun `이전 오브젝트 정리가 실패해도 확인은 성공한다`() {
        givenUploaded()
        every { members.attachProfileImage(memberId, pendingKey) } returns previousKey
        every { storage.delete(previousKey) } throws IllegalStateException("storage down")

        val result = service.confirmProfileImageUpload(confirm, memberId)

        assertEquals(confirmedKey, result)
        verify(exactly = 1) { storage.delete(previousKey) }
    }

    @Test
    fun `회원 반영이 실패하면 이전 오브젝트를 지우지 않는다`() {
        givenUploaded()
        every { members.attachProfileImage(memberId, pendingKey) } throws MemberNotFoundException("회원을 찾을 수 없습니다.")

        assertFailsWith<MemberNotFoundException> { service.confirmProfileImageUpload(confirm, memberId) }

        verify(exactly = 0) { storage.delete(any()) }
    }

    // --- 제거 ---

    @Test
    fun `제거는 회원에서 뗀 뒤 이전 오브젝트를 지운다`() {
        every { members.detachProfileImage(memberId) } returns previousKey

        service.removeProfileImage(memberId)

        verifyOrder {
            members.detachProfileImage(memberId)
            storage.delete(previousKey)
        }
    }

    @Test
    fun `이미지가 없던 회원의 제거는 스토리지를 부르지 않는다`() {
        every { members.detachProfileImage(memberId) } returns null

        service.removeProfileImage(memberId)

        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `제거 중 오브젝트 삭제가 실패해도 예외를 내보내지 않는다`() {
        every { members.detachProfileImage(memberId) } returns previousKey
        every { storage.delete(previousKey) } throws IllegalStateException("storage down")

        service.removeProfileImage(memberId)

        verify(exactly = 1) { storage.delete(previousKey) }
    }
}
