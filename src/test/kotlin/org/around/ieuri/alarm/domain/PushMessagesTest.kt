package org.around.ieuri.alarm.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.PushMessages

/** 알림 문구 조립(SPEC-0028 R13). 본문은 무번역·절단, 제목은 언어별 번역. */
class PushMessagesTest {

    /** AC13.1 — 사용자가 쓴 본문은 수신자 언어와 무관하게 원문 그대로다. */
    @Test
    fun `본문은 상한 이하면 그대로다`() {
        assertEquals("안녕하세요 반갑습니다", PushMessages.truncateBody("안녕하세요 반갑습니다"))
    }

    /** AC13.2 — 상한을 넘으면 자른다. */
    @Test
    fun `상한을 넘는 본문은 잘린다`() {
        val long = "가".repeat(PushMessages.BODY_MAX_LENGTH + 50)

        val truncated = PushMessages.truncateBody(long)

        assertEquals(PushMessages.BODY_MAX_LENGTH, truncated.length)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `상한과 정확히 같은 길이는 자르지 않는다`() {
        val exact = "가".repeat(PushMessages.BODY_MAX_LENGTH)

        val result = PushMessages.truncateBody(exact)

        assertEquals(exact, result)
        assertTrue(!result.endsWith("…"))
    }

    /** AC13.3 — 사진만 있는 메시지. 본문이 비어도 알림은 나간다. */
    @Test
    fun `빈 본문은 빈 문자열이다`() {
        assertEquals("", PushMessages.truncateBody(""))
        assertEquals("", PushMessages.truncateBody("   "))
    }

    @Test
    fun `제목은 언어마다 다르다`() {
        val titles = AlarmLocale.entries.map { PushMessages.answerTitle(it) }

        assertEquals(titles.size, titles.distinct().size, "언어별 제목이 겹치면 번역이 빠진 것이다")
    }

    @Test
    fun `DM 제목에 보낸 사람 닉네임이 들어간다`() {
        AlarmLocale.entries.forEach { locale ->
            assertTrue(
                PushMessages.dmTitle(locale, "홍길동").contains("홍길동"),
                "$locale 제목에 닉네임이 없다",
            )
        }
    }

    @Test
    fun `답변과 답글의 제목이 다르다`() {
        AlarmLocale.entries.forEach { locale ->
            assertTrue(
                PushMessages.answerTitle(locale) != PushMessages.replyTitle(locale),
                "$locale 에서 답변과 답글 제목이 같다",
            )
        }
    }

    /** AC20.3 — 지원하지 않는 언어는 EN으로 떨어진다(POL-039). */
    @Test
    fun `지원하지 않는 언어는 EN이다`() {
        assertEquals(AlarmLocale.EN, AlarmLocale.ofOrFallback("FR"))
        assertEquals(AlarmLocale.EN, AlarmLocale.ofOrFallback(null))
        assertEquals(AlarmLocale.EN, AlarmLocale.ofOrFallback(""))
    }

    @Test
    fun `지원하는 언어는 그대로 해석된다`() {
        assertEquals(AlarmLocale.KO, AlarmLocale.ofOrFallback("KO"))
        assertEquals(AlarmLocale.JA, AlarmLocale.ofOrFallback("ja"))
        assertEquals(AlarmLocale.ZH, AlarmLocale.ofOrFallback(" ZH "))
    }
}
