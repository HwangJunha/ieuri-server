package org.around.ieuri.auth.application.member

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.member.required.EmailSenderPort
import kotlin.test.Test

class VerificationMailServiceTest {

    private val emailSender = mockk<EmailSenderPort>(relaxUnitFun = true)
    private val service = VerificationMailService(emailSender)

    /** 두 메일은 문구가 달라 포트 메서드가 다르다 — 주소와 코드가 뒤바뀌지 않는지도 본다. */
    @Test
    fun `가입 인증과 비밀번호 재설정은 각자의 메일로 나간다`() {
        service.sendVerificationCode("a@example.com", "111111")
        service.sendPasswordResetCode("b@example.com", "222222")

        verify(exactly = 1) { emailSender.sendVerificationCode("a@example.com", "111111") }
        verify(exactly = 1) { emailSender.sendPasswordResetCode("b@example.com", "222222") }
    }
}
