package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.provided.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** `(provider, email)` 유니크 판정(SPEC-0024 R5). 다른 provider의 같은 주소는 충돌이 아니다(POL-002). */
class ProviderEmailUniquenessTest {

    private val memberRepository = mockk<MemberRepository>()
    private val uniqueness = ProviderEmailUniqueness(memberRepository, prefixingIdentifierProtector())

    @Test
    fun `같은 provider에 그 주소가 없으면 통과한다`() {
        every { memberRepository.existsByEmailIdentifier(Provider.KAKAO, "protected:user@example.com", 7L) } returns false

        uniqueness.ensureAvailable(Provider.KAKAO, Email("user@example.com"), excludingMemberId = 7L)

        // 조회 조건에 provider와 본인 제외 기준이 그대로 들어간다. 평문 주소로는 묻지 않는다.
        verify(exactly = 1) { memberRepository.existsByEmailIdentifier(Provider.KAKAO, "protected:user@example.com", 7L) }
    }

    /** 가입처럼 본인이 아직 없으면 제외 기준 없이 묻는다. */
    @Test
    fun `제외할 회원을 주지 않으면 제외 기준 없이 판정한다`() {
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:user@example.com", null) } returns false

        uniqueness.ensureAvailable(Provider.EMAIL, Email(" USER@example.com "))

        verify(exactly = 1) { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:user@example.com", null) }
    }

    /** 예외에 상대 회원의 정보를 담지 않는다(POL-013). */
    @Test
    fun `같은 provider에 그 주소가 있으면 주소를 담지 않은 메시지로 거부한다`() {
        every { memberRepository.existsByEmailIdentifier(Provider.GOOGLE, "protected:taken@example.com", null) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { uniqueness.ensureAvailable(Provider.GOOGLE, Email("taken@example.com")) }

        assertEquals("EMAIL_ALREADY_IN_USE", ex.code)
        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        assertFalse(ex.message.orEmpty().contains("@"))
    }
}
