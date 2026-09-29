package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.vo.Nickname
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 닉네임 값 객체(SPEC-0011 R5). 길이·공백 규칙을 판정하며 고유성은 판정하지 않는다. */
class NicknameTest {

    @Test
    fun `앞뒤 공백을 제거해 저장한다`() {
        assertEquals("여행하는곰", Nickname("  여행하는곰  ").value)
    }

    @Test
    fun `가운데 공백은 유지한다`() {
        assertEquals("여행 하는 곰", Nickname("여행 하는 곰").value)
    }

    @Test
    fun `허용 길이 경계를 받아들인다`() {
        assertEquals(2, Nickname("가나").value.length)
        assertEquals(20, Nickname("가".repeat(20)).value.length)
    }

    @Test
    fun `공백 제거 후 최소 길이 미만이면 거절한다`() {
        assertFailsWith<IllegalArgumentException> { Nickname("가") }
        assertFailsWith<IllegalArgumentException> { Nickname("  가  ") }
        assertFailsWith<IllegalArgumentException> { Nickname("   ") }
        assertFailsWith<IllegalArgumentException> { Nickname("") }
    }

    @Test
    fun `최대 길이를 넘으면 거절한다`() {
        assertFailsWith<IllegalArgumentException> { Nickname("가".repeat(21)) }
    }

    @Test
    fun `공백을 제거하면 길이 안에 드는 값은 받아들인다`() {
        assertEquals(20, Nickname("  " + "가".repeat(20) + "  ").value.length)
    }
}
