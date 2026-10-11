package org.around.ieuri.auth.domain.member
import org.around.ieuri.auth.domain.member.InvalidNicknameException
import org.around.ieuri.auth.domain.member.Nickname
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
        assertFailsWith<InvalidNicknameException> { Nickname("가") }
        assertFailsWith<InvalidNicknameException> { Nickname("  가  ") }
        assertFailsWith<InvalidNicknameException> { Nickname("   ") }
        assertFailsWith<InvalidNicknameException> { Nickname("") }
    }

    @Test
    fun `최대 길이를 넘으면 거절한다`() {
        assertFailsWith<InvalidNicknameException> { Nickname("가".repeat(21)) }
    }

    /** 메시지와 코드는 응답에 그대로 나간다. 짧은 쪽과 긴 쪽의 문구가 다르다. */
    @Test
    fun `길이 위반은 방향마다 다른 메시지로 답한다`() {
        val short = assertFailsWith<InvalidNicknameException> { Nickname("가") }
        val long = assertFailsWith<InvalidNicknameException> { Nickname("가".repeat(21)) }

        assertEquals("닉네임은 2자 이상이어야 합니다.", short.message)
        assertEquals("닉네임은 20자를 넘을 수 없습니다.", long.message)
        assertEquals("INVALID_NICKNAME", short.code)
    }

    @Test
    fun `공백을 제거하면 길이 안에 드는 값은 받아들인다`() {
        assertEquals(20, Nickname("  " + "가".repeat(20) + "  ").value.length)
    }

    @Test
    fun `후보 중 처음으로 형식에 맞는 닉네임을 고른다`() {
        assertEquals("여행하는곰", Nickname.firstValid("여행하는곰", "산책하는새")?.value)
        assertEquals("산책하는새", Nickname.firstValid(null, "산책하는새")?.value)
    }

    @Test
    fun `형식을 벗어난 후보는 잘라내지 않고 건너뛴다`() {
        assertEquals("산책하는새", Nickname.firstValid("가", "가".repeat(21), "  산책하는새 ")?.value)
    }

    @Test
    fun `맞는 후보가 없으면 null이다`() {
        assertNull(Nickname.firstValid())
        assertNull(Nickname.firstValid(null, "가", "   "))
    }
}
