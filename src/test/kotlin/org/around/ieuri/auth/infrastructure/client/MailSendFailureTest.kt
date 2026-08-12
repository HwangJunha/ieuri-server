package org.around.ieuri.auth.infrastructure.client

import org.around.ieuri.auth.infrastructure.adapter.out.client.MailSendFailure
import org.around.ieuri.auth.infrastructure.adapter.out.client.classifySendFailure
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import kotlin.test.Test
import kotlin.test.assertEquals

/** 발송 실패 분류(SPEC-0016 R7) — 예외를 입력으로 받는 순수 함수 단위테스트. */
class MailSendFailureTest {

    @Test
    fun `인증 예외는 AUTH로 분류한다`() {
        assertEquals(
            MailSendFailure.AUTH,
            classifySendFailure(MailAuthenticationException("535 Authentication credentials invalid")),
        )
    }

    @Test
    fun `원인 체인에 중첩된 인증 예외도 AUTH로 분류한다`() {
        val nested = MailSendException("발송 실패", MailAuthenticationException("535 auth failed"))
        assertEquals(MailSendFailure.AUTH, classifySendFailure(nested))
    }

    @Test
    fun `스로틀링 문구는 RATE_LIMIT으로 분류한다`() {
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("Message throttled")))
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("Rate limit exceeded")))
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("Too many messages")))
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("please Try again later")))
    }

    @Test
    fun `SMTP 4xx 응답 코드는 RATE_LIMIT으로 분류한다`() {
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("452 insufficient system storage")))
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(RuntimeException("Server replied: 421-service unavailable")))
    }

    @Test
    fun `원인 체인에 중첩된 스로틀링도 RATE_LIMIT으로 분류한다`() {
        val nested = MailSendException("발송 실패", RuntimeException("421 throttled"))
        assertEquals(MailSendFailure.RATE_LIMIT, classifySendFailure(nested))
    }

    @Test
    fun `그 밖의 실패는 OTHER로 분류한다`() {
        assertEquals(MailSendFailure.OTHER, classifySendFailure(RuntimeException("Connection refused")))
        assertEquals(MailSendFailure.OTHER, classifySendFailure(RuntimeException("550 mailbox unavailable")))
    }

    @Test
    fun `메시지가 없는 예외도 예외 없이 OTHER로 분류한다`() {
        assertEquals(MailSendFailure.OTHER, classifySendFailure(RuntimeException()))
    }

    @Test
    fun `자기 자신을 원인으로 갖는 예외에도 무한루프에 빠지지 않는다`() {
        val selfCausing = object : RuntimeException("boom") {
            override val cause: Throwable get() = this
        }
        assertEquals(MailSendFailure.OTHER, classifySendFailure(selfCausing))
    }
}
