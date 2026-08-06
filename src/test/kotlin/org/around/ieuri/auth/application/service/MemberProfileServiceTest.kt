package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.ChangeNicknameCommand
import org.around.ieuri.auth.application.command.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.command.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.command.RemoveProfileImageCommand
import org.around.ieuri.auth.application.exception.ImageTooLargeException
import org.around.ieuri.auth.application.exception.InvalidNicknameException
import org.around.ieuri.auth.application.exception.NicknameAlreadyTakenException
import org.around.ieuri.auth.application.exception.ObjectKeyNotFoundException
import org.around.ieuri.auth.application.exception.PhotoNotUploadedException
import org.around.ieuri.auth.application.exception.UnsupportedImageTypeException
import org.around.ieuri.auth.application.policy.ProfileImagePolicy
import org.around.ieuri.auth.application.port.out.client.ProfileImageStoragePort
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.NicknameConflictException
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
 *
 * 고정하는 계약: 닉네임은 **중복이면 거절하고 자기 값 재제출은 성공**하며, 이미지는
 * **키 발급 전에 형식·크기를 보고**, 확인 단계는 **소유권 → 존재 → 크기 → 매직바이트**
 * 순으로 판정한다. 제거는 멱등이다.
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
    fun `쓰이지 않는 닉네임이면 변경에 성공한다`() {
        every { repository.existsByNicknameExcludingMember("여행하는곰", memberId) } returns false

        val result = service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))

        assertEquals("여행하는곰", result)
        verify { repository.updateNickname(memberId, "여행하는곰") }
    }

    @Test
    fun `앞뒤 공백은 제거된 값으로 저장한다`() {
        every { repository.existsByNicknameExcludingMember("여행하는곰", memberId) } returns false

        val result = service.changeNickname(ChangeNicknameCommand(memberId, "  여행하는곰  "))

        assertEquals("여행하는곰", result)
        verify { repository.updateNickname(memberId, "여행하는곰") }
    }

    @Test
    fun `다른 회원이 쓰는 닉네임이면 거절하고 저장하지 않는다`() {
        every { repository.existsByNicknameExcludingMember("여행하는곰", memberId) } returns true

        assertFailsWith<NicknameAlreadyTakenException> {
            service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))
        }
        verify(exactly = 0) { repository.updateNickname(any(), any()) }
    }

    @Test
    fun `중복 실패 메시지에 상대 정보를 담지 않는다`() {
        every { repository.existsByNicknameExcludingMember(any(), any()) } returns true

        val e = assertFailsWith<NicknameAlreadyTakenException> {
            service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))
        }

        assertEquals("NICKNAME_ALREADY_TAKEN", e.code)
        assertTrue(e.message!!.none { it.isDigit() }, "메시지에 식별자가 들어갔다: ${e.message}")
    }

    @Test
    fun `경합으로 유니크 제약이 터져도 같은 실패로 옮긴다`() {
        // 사전 확인은 통과했는데 저장에서 제약이 터지는 경우 — 동시 요청 둘이 함께 통과한 상황이다.
        every { repository.existsByNicknameExcludingMember(any(), any()) } returns false
        every { repository.updateNickname(any(), any()) } throws NicknameConflictException("dup")

        assertFailsWith<NicknameAlreadyTakenException> {
            service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰"))
        }
    }

    @Test
    fun `자기 현재 닉네임과 같은 값은 성공한다`() {
        // 중복 판정이 본인을 제외하므로 사용 중이 아니라고 답한다.
        every { repository.existsByNicknameExcludingMember("여행하는곰", memberId) } returns false

        assertEquals("여행하는곰", service.changeNickname(ChangeNicknameCommand(memberId, "여행하는곰")))
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

    private fun pendingKey(owner: Long = memberId) =
        "pending/profile/$owner/${UUID.randomUUID()}"

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
