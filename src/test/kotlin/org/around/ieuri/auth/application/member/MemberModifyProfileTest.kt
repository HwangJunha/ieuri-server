package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.InvalidNicknameException
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.ObjectKeyNotFoundException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 프로필의 DB 단계(SPEC-0011 R5·R6). 프로필 행이 없는 회원은 없는 회원과 같이 답한다. */
class MemberModifyProfileTest {

    private val memberId = 7L
    private val otherMemberId = 99L

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val service = memberModifyService(memberRepository)

    private val pendingKey = "pending/profile/$memberId/${UUID.fromString("44444444-4444-4444-4444-444444444444")}"
    private val confirmedKey = pendingKey.replaceFirst("pending/", "images/")
    private val previousKey = "images/profile/$memberId/${UUID.fromString("55555555-5555-5555-5555-555555555555")}"

    private fun givenMember(exists: Boolean = true) {
        every { memberRepository.findById(memberId) } returns if (exists) memberFixture(id = memberId) else null
    }

    // --- 닉네임 ---

    /** 중복은 보지 않는다(SPEC-0032 R31) — 저장소에 존재 여부를 묻는 호출이 없다. */
    @Test
    fun `닉네임은 앞뒤 공백을 뗀 값으로 저장하고 그 값을 돌려준다`() {
        givenMember()
        every { memberRepository.saveNickname(memberId, Nickname("여행하는곰")) } returns true

        val result = service.changeNickname(ChangeNicknameCommand("  여행하는곰  "), memberId)

        assertEquals("여행하는곰", result)
        verify(exactly = 1) { memberRepository.saveNickname(memberId, Nickname("여행하는곰")) }
    }

    /** 형식 오류가 회원 부재보다 먼저다 — 없는 회원이어도 INVALID_NICKNAME이 나간다. */
    @Test
    fun `길이 규칙을 벗어난 닉네임은 회원을 읽기 전에 거부한다`() {
        givenMember(exists = false)

        val tooShort = assertFailsWith<InvalidNicknameException> { service.changeNickname(ChangeNicknameCommand(" 가 "), memberId) }
        val tooLong = assertFailsWith<InvalidNicknameException> { service.changeNickname(ChangeNicknameCommand("가".repeat(21)), memberId) }

        assertEquals("닉네임은 2자 이상이어야 합니다.", tooShort.message)
        assertEquals("닉네임은 20자를 넘을 수 없습니다.", tooLong.message)
        verify(exactly = 0) { memberRepository.findById(any()) }
        verify(exactly = 0) { memberRepository.saveNickname(any(), any()) }
    }

    @Test
    fun `없는 회원의 닉네임 변경은 저장하지 않고 회원 없음으로 답한다`() {
        givenMember(exists = false)

        val ex = assertFailsWith<MemberNotFoundException> { service.changeNickname(ChangeNicknameCommand("여행하는곰"), memberId) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveNickname(any(), any()) }
    }

    @Test
    fun `프로필 행이 없는 회원의 닉네임 변경은 회원 없음으로 답한다`() {
        givenMember()
        every { memberRepository.saveNickname(memberId, any()) } returns false

        val ex = assertFailsWith<MemberNotFoundException> { service.changeNickname(ChangeNicknameCommand("여행하는곰"), memberId) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
    }

    // --- 프로필 이미지 반영 ---

    /** 이전 키는 덮어쓰기 전에 읽어야 호출자가 정리할 수 있다. */
    @Test
    fun `이미지 반영은 이전 키를 읽은 뒤 확정 키를 저장하고 이전 키를 돌려준다`() {
        givenMember()
        every { memberRepository.findProfileImageKey(memberId) } returns previousKey
        every { memberRepository.saveProfileImage(memberId, confirmedKey) } returns true

        val result = service.attachProfileImage(memberId, pendingKey)

        assertEquals(previousKey, result)
        verifyOrder {
            memberRepository.findProfileImageKey(memberId)
            memberRepository.saveProfileImage(memberId, confirmedKey)
        }
    }

    @Test
    fun `이전 이미지가 없던 회원은 null을 돌려준다`() {
        givenMember()
        every { memberRepository.findProfileImageKey(memberId) } returns null
        every { memberRepository.saveProfileImage(memberId, confirmedKey) } returns true

        assertNull(service.attachProfileImage(memberId, pendingKey))
    }

    @Test
    fun `남의 키와 이미 확정된 키는 저장하지 않고 거부한다`() {
        givenMember()
        every { memberRepository.findProfileImageKey(memberId) } returns null
        val othersKey = "pending/profile/$otherMemberId/${UUID.fromString("66666666-6666-6666-6666-666666666666")}"

        listOf(othersKey, confirmedKey, "not-a-key").forEach { raw ->
            val ex = assertFailsWith<ObjectKeyNotFoundException> { service.attachProfileImage(memberId, raw) }
            assertEquals("발급된 적 없는 오브젝트 키입니다.", ex.message)
        }
        verify(exactly = 0) { memberRepository.saveProfileImage(any(), any()) }
    }

    @Test
    fun `없는 회원과 프로필 행이 없는 회원의 이미지 반영은 회원 없음으로 답한다`() {
        givenMember(exists = false)
        val missingMember = assertFailsWith<MemberNotFoundException> { service.attachProfileImage(memberId, pendingKey) }
        verify(exactly = 0) { memberRepository.saveProfileImage(any(), any()) }

        givenMember()
        every { memberRepository.findProfileImageKey(memberId) } returns null
        every { memberRepository.saveProfileImage(memberId, confirmedKey) } returns false
        val missingProfile = assertFailsWith<MemberNotFoundException> { service.attachProfileImage(memberId, pendingKey) }

        assertEquals("회원을 찾을 수 없습니다.", missingMember.message)
        assertEquals("회원을 찾을 수 없습니다.", missingProfile.message)
    }

    // --- 프로필 이미지 제거 ---

    @Test
    fun `이미지 제거는 키를 비우고 이전 키를 돌려준다`() {
        every { memberRepository.findProfileImageKey(memberId) } returns previousKey
        every { memberRepository.saveProfileImage(memberId, null) } returns true

        val result = service.detachProfileImage(memberId)

        assertEquals(previousKey, result)
        verifyOrder {
            memberRepository.findProfileImageKey(memberId)
            memberRepository.saveProfileImage(memberId, null)
        }
    }

    /** 멱등 — 이미 없어도 성공하고 null을 돌려준다. */
    @Test
    fun `이미지가 없던 회원의 제거도 성공한다`() {
        every { memberRepository.findProfileImageKey(memberId) } returns null
        every { memberRepository.saveProfileImage(memberId, null) } returns true

        assertNull(service.detachProfileImage(memberId))
    }

    @Test
    fun `프로필 행이 없는 회원의 이미지 제거는 회원 없음으로 답한다`() {
        every { memberRepository.findProfileImageKey(memberId) } returns null
        every { memberRepository.saveProfileImage(memberId, null) } returns false

        val ex = assertFailsWith<MemberNotFoundException> { service.detachProfileImage(memberId) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
    }
}
