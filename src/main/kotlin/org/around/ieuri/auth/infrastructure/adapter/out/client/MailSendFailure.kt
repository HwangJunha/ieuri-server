package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.springframework.mail.MailAuthenticationException

/** 발송 실패 원인. 로그 구별용 분류이며 재시도 같은 동작 분기에는 쓰지 않는다(SPEC-0016 R7). */
enum class MailSendFailure {
    /** 인증 실패(SMTP 535). 자격증명·환경변수를 점검해야 한다. */
    AUTH,

    /** 스로틀링(SMTP 4xx 일시 오류). 자격증명은 정상이며 이 메일만 유실된다. */
    RATE_LIMIT,

    /** 위 둘로 갈라지지 않은 나머지. 원문 메시지를 봐야 한다. */
    OTHER,
}

private val RATE_LIMIT_HINTS = listOf("throttl", "rate limit", "too many", "try again later")

/**
 * 예외를 발송 실패 원인으로 분류한다. 인증은 [MailAuthenticationException] 타입으로, 스로틀링은 SMTP 4xx로 판정한다.
 * - 원인 체인 전체를 훑고, 걸리지 않으면 [MailSendFailure.OTHER]다. 예외를 던지지 않는다.
 */
fun classifySendFailure(ex: Throwable): MailSendFailure {
    val chain = generateSequence(ex) { it.cause?.takeIf { cause -> cause !== it } }.take(10).toList()

    if (chain.any { it is MailAuthenticationException }) return MailSendFailure.AUTH

    val messages = chain.mapNotNull { it.message?.lowercase() }
    if (messages.any { msg -> RATE_LIMIT_HINTS.any { msg.contains(it) } || SMTP_4XX.containsMatchIn(msg) }) {
        return MailSendFailure.RATE_LIMIT
    }
    return MailSendFailure.OTHER
}

/** SMTP 일시 오류 응답 코드(4xx). 줄 시작이나 공백 뒤에 오는 세 자리만 본다. */
private val SMTP_4XX = Regex("(^|\\s)4\\d\\d(\\s|-|$)")
