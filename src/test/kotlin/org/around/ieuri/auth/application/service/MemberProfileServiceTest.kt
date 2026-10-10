package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.MemberProfileService
import org.around.ieuri.auth.application.member.ProfileImagePolicy
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.RemoveProfileImageCommand
import org.around.ieuri.auth.application.member.provided.exception.ImageTooLargeException
import org.around.ieuri.auth.application.member.provided.exception.InvalidNicknameException
import org.around.ieuri.auth.application.member.provided.exception.ObjectKeyNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.PhotoNotUploadedException
import org.around.ieuri.auth.application.member.provided.exception.UnsupportedImageTypeException
import org.around.ieuri.auth.application.member.required.ProfileImageStoragePort
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
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
 * 프로필 수정(SPEC-0011 R5·R6).
 * - 닉네임은 중복을 허용하고 형식만 본다(SPEC-0032 R31). 제거는 멱등이다.
 * - 이미지는 발급 전에 형식·크기를, 확인 단계는 소유권 → 존재 → 크기 → 매직바이트 순으로 판정한다.
 */
class MemberProfileServiceTest {

    private val memberId = 7L
    private val otherMemberId = 99L
    private val now = Instant.parse("2026-08-07T00:00:00Z")
    private val ttl = Duration.ofMinutes(30)
    private val maxBytes = 10_485_760L

    private val repository = mockk<MemberProfileRepositoryPort>(relaxed = true)
    private val storage = mockk<ProfileImageStoragePort>(relaxed = true)
    private val policy = ProfileImagePolicy(
        uploadUrlTtl = ttl,
        maxUploadBytes = maxBytes,
        allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp"),
    )
    private val service = MemberProfileService(
        repository,
        storage,
        policy,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)

    // --- R5 닉네임 ---

    @Test
    fun `닉네임을 바꾸면 저장한다`() {
        val result = service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))

        assertEquals("여행하는곰", result)
        verify { repository.updateNickname(memberId, "여행하는곰") }
    }

    @Test
    fun `앞뒤 공백은 제거된 값으로 저장한다`() {
        val result = service.changeNickname(ChangeNicknameCommand(memberId, "  여행하는곰  "))

        assertEquals("여행하는곰", result)
        verify { repository.updateNickname(memberId, "여행하는곰") }
    }

    /** SPEC-0032 R31. 고유성 판정이 없으므로 같은 닉네임이 서로를 막지 않는다. */
    @Test
    fun `두 회원이 같은 닉네임을 가질 수 있다`() {
        service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))
        service.changeNickname(ChangeNicknameCommand(otherMemberId, "여행하는곰"))

        verify { repository.updateNickname(memberId, "여행하는곰") }
        verify { repository.updateNickname(otherMemberId, "여행하는곰") }
    }

    @Test
    fun `허용 길이를 벗어나면 형식 오류로 거절한다`() {
        listOf("가", "가".repeat(21)).forEach { raw ->
            assertFailsWith<InvalidNicknameException> {
                service.changeNickname(ChangeNicknameCommand(memberId, raw))
            }
        }
        verify(exactly = 0) { repository.updateNickname(any(), any()) }
    }

    // --- R6 이미지 발급 ---

    @Test
    fun `허용 형식과 크기면 profile 네임스페이스 키와 URL을 발급한다`() {
        every { storage.presignPut(any(), any(), any(), any()) } returns "https://r2.example/put"

        val view = service.issueProfileImageUploadUrl(
            IssueProfileImageUploadUrlCommand(memberId, "image/jpeg", 1_024),
        )

        assertTrue(view.objectKey.startsWith("pending/profile/$memberId/"), view.objectKey)
        assertEquals("https://r2.example/put", view.uploadUrl)
        assertEquals(now.plus(ttl), view.expiresAt)
    }

    @Test
    fun `허용하지 않는 형식이면 키를 발급하지 않는다`() {
        assertFailsWith<UnsupportedImageTypeException> {
            service.issueProfileImageUploadUrl(
                IssueProfileImageUploadUrlCommand(memberId, "image/gif", 1_024),
            )
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `상한을 넘는 크기면 키를 발급하지 않는다`() {
        assertFailsWith<ImageTooLargeException> {
            service.issueProfileImageUploadUrl(
                IssueProfileImageUploadUrlCommand(memberId, "image/jpeg", maxBytes + 1),
            )
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `크기가 0 이하면 발급하지 않는다`() {
        assertFailsWith<ImageTooLargeException> {
            service.issueProfileImageUploadUrl(
                IssueProfileImageUploadUrlCommand(memberId, "image/jpeg", 0),
            )
        }
    }

    // --- R6 이미지 확인 ---

    private fun pendingKey(owner: Long = memberId) = "pending/profile/$owner/${UUID.randomUUID()}"

    @Test
    fun `업로드가 확인되면 확정 위치로 옮기고 회원에 반영한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, any()) } returns jpegMagic
        every { repository.findProfileImageKey(memberId) } returns null

        val result = service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))

        val expected = key.replaceFirst("pending/", "images/")
        assertEquals(expected, result)
        verify { storage.move(key, expected) }
        verify { repository.updateProfileImage(memberId, expected) }
    }

    @Test
    fun `업로드되지 않은 키는 미업로드로 실패한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns null

        assertFailsWith<PhotoNotUploadedException> {
            service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))
        }
        verify(exactly = 0) { repository.updateProfileImage(any(), any()) }
    }

    @Test
    fun `남의 키는 저장소를 묻기 전에 거절한다`() {
        val key = pendingKey(owner = otherMemberId)

        assertFailsWith<ObjectKeyNotFoundException> {
            service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))
        }
        // 저장소를 왕복하지 않는다 — 타인 키의 존재 여부가 응답 시간으로 새어 나가지 않게 한다.
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `형식이 어긋난 키와 커뮤니티 키는 거절한다`() {
        listOf(
            "not-a-key",
            "pending/community/$memberId/${UUID.randomUUID()}",
            "images/profile/$memberId/${UUID.randomUUID()}",
        ).forEach { raw ->
            assertFailsWith<ObjectKeyNotFoundException> {
                service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, raw))
            }
        }
    }

    @Test
    fun `매직바이트가 맞지 않으면 형식 오류로 거절한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, any()) } returns byteArrayOf(0x00, 0x01, 0x02, 0x03)

        assertFailsWith<UnsupportedImageTypeException> {
            service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))
        }
        verify(exactly = 0) { repository.updateProfileImage(any(), any()) }
    }

    @Test
    fun `실제 크기가 상한을 넘으면 거절한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns maxBytes + 1

        assertFailsWith<ImageTooLargeException> {
            service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))
        }
    }

    @Test
    fun `교체 시 이전 오브젝트를 정리한다`() {
        val key = pendingKey()
        val previous = "images/profile/$memberId/${UUID.randomUUID()}"
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, any()) } returns jpegMagic
        every { repository.findProfileImageKey(memberId) } returns previous

        service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))

        verify { storage.delete(previous) }
    }

    @Test
    fun `이전 오브젝트 정리 실패가 프로필 반영을 막지 않는다`() {
        val key = pendingKey()
        val previous = "images/profile/$memberId/${UUID.randomUUID()}"
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, any()) } returns jpegMagic
        every { repository.findProfileImageKey(memberId) } returns previous
        every { storage.delete(previous) } throws RuntimeException("storage down")

        val result = service.confirmProfileImageUpload(ConfirmProfileImageUploadCommand(memberId, key))

        assertEquals(key.replaceFirst("pending/", "images/"), result)
        verify { repository.updateProfileImage(memberId, result) }
    }

    // --- R6 이미지 제거 ---

    @Test
    fun `제거하면 키를 비우고 이전 오브젝트를 지운다`() {
        val previous = "images/profile/$memberId/${UUID.randomUUID()}"
        every { repository.findProfileImageKey(memberId) } returns previous

        service.removeProfileImage(RemoveProfileImageCommand(memberId))

        verify { repository.updateProfileImage(memberId, null) }
        verify { storage.delete(previous) }
    }

    @Test
    fun `이미 이미지가 없어도 성공한다`() {
        every { repository.findProfileImageKey(memberId) } returns null

        service.removeProfileImage(RemoveProfileImageCommand(memberId))

        verify { repository.updateProfileImage(memberId, null) }
        verify(exactly = 0) { storage.delete(any()) }
    }
}
