package org.around.ieuri.common.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 앱 식별자. 상단 칩 구성을 고르는 키다.
 * - 헤더 파싱은 실패해도 예외를 던지지 않고 기본 앱으로 폴백한다.
 */
class AppKeyTest {

    @Test
    fun `헤더 값이 그대로 식별자가 된다`() {
        assertEquals("demo-app", AppKey.fromHeaderOrDefault("demo-app").value)
    }

    @Test
    fun `앞뒤 공백은 제거된다`() {
        assertEquals("demo-app", AppKey.fromHeaderOrDefault("  demo-app  ").value)
    }

    @Test
    fun `헤더가 없으면 기본 앱으로 폴백한다`() {
        assertEquals(AppKey.DEFAULT, AppKey.fromHeaderOrDefault(null))
        assertEquals(AppKey.DEFAULT, AppKey.fromHeaderOrDefault(""))
        assertEquals(AppKey.DEFAULT, AppKey.fromHeaderOrDefault("   "))
    }

    @Test
    fun `상한을 넘는 헤더는 예외가 아니라 폴백이다`() {
        // 조작된 헤더 하나로 요청이 깨지지 않아야 한다.
        assertEquals(AppKey.DEFAULT, AppKey.fromHeaderOrDefault("a".repeat(51)))
    }

    @Test
    fun `상한 경계는 허용된다`() {
        val exact = "a".repeat(50)
        assertEquals(exact, AppKey.fromHeaderOrDefault(exact).value)
    }

    @Test
    fun `직접 생성할 때는 빈 값을 거부한다 - 폴백은 파싱 단계의 규약이다`() {
        assertFailsWith<IllegalArgumentException> { AppKey("") }
        assertFailsWith<IllegalArgumentException> { AppKey("   ") }
        assertFailsWith<IllegalArgumentException> { AppKey("a".repeat(51)) }
    }
}
