package org.around.ieuri.common.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** maskEmail 규칙(SPEC-0016 R6) — 순수 문자열 변환 단위테스트. */
class EmailMaskingTest {

    @Test
    fun `일반 주소는 앞 2자만 남기고 도메인은 유지한다`() {
        assertEquals("ta***@gmail.com", maskEmail("tarot1415@gmail.com"))
        assertEquals("ab***@ieuri.co.kr", maskEmail("abc@ieuri.co.kr"))
    }

    @Test
    fun `로컬파트가 2자 이하면 로컬파트를 전부 가린다`() {
        assertEquals("***@x.com", maskEmail("ab@x.com"))
        assertEquals("***@x.com", maskEmail("a@x.com"))
    }

    @Test
    fun `로컬파트 2자 주소의 로컬파트가 결과에 노출되지 않는다`() {
        val masked = maskEmail("ab@x.com")
        assertFalse(masked.contains("ab"), "로컬파트가 그대로 남았다: $masked")
    }

    @Test
    fun `골뱅이 없는 문자열은 예외 없이 전부 가린다`() {
        assertEquals("***", maskEmail("not-an-email"))
        assertEquals("***", maskEmail("12345"))
    }

    @Test
    fun `null과 공백 문자열도 예외 없이 가린 값을 반환한다`() {
        assertEquals("***", maskEmail(null))
        assertEquals("***", maskEmail(""))
        assertEquals("***", maskEmail("   "))
    }

    @Test
    fun `골뱅이로 시작하거나 끝나는 문자열은 전부 가린다`() {
        assertEquals("***", maskEmail("@x.com"))
        assertEquals("***", maskEmail("user@"))
    }

    @Test
    fun `로컬파트에 골뱅이가 여러 개면 마지막 것을 도메인 구분자로 본다`() {
        assertEquals("a.***@x.com", maskEmail("a.b@c@x.com"))
    }
}
