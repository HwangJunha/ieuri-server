package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.strategy.NicknameGenerator
import org.around.ieuri.auth.domain.vo.Nickname
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 가입 시 닉네임 자동 생성(SPEC-0011 R20).
 *
 * 고정한 계약은 셋이다 — **형식**(형용사+명사+숫자 외의 문자가 없다), **충돌 시 재시도**,
 * 그리고 **조합 수가 100만 이상**이라는 것.
 */
class NicknameGeneratorTest {

    private val generator = NicknameGenerator(Random(20260807))

    @Test
    fun `조합 가능한 경우의 수가 100만 이상이다`() {
        assertTrue(
            NicknameGenerator.COMBINATIONS >= 1_000_000,
            "조합 수가 부족하다: ${NicknameGenerator.COMBINATIONS}",
        )
    }

    @Test
    fun `형용사 명사 숫자 순으로 이어 붙이고 그 외 문자를 포함하지 않는다`() {
        repeat(200) {
            val value = generator.generate { false }.value
            val digits = value.takeLast(NicknameGenerator.DIGITS)
            val words = value.dropLast(NicknameGenerator.DIGITS)

            assertTrue(digits.all { c -> c.isDigit() }, "끝 4자가 숫자가 아니다: $value")

            val adjective = NicknameGenerator.ADJECTIVES.firstOrNull { words.startsWith(it) }
            assertTrue(adjective != null, "형용사 목록으로 시작하지 않는다: $value")
            val noun = words.removePrefix(adjective)
            assertTrue(noun in NicknameGenerator.NOUNS, "명사 목록에 없다: $noun ($value)")
            // 형용사 + 명사 + 숫자가 전부다 — 구분자나 여분 문자가 없다.
            assertEquals(value, adjective + noun + digits)
        }
    }

    @Test
    fun `생성값이 항상 닉네임 규칙을 만족한다`() {
        repeat(200) {
            val value = generator.generate { false }.value
            assertEquals(value, Nickname(value).value)
        }
    }

    @Test
    fun `이미 쓰이는 값이면 다시 뽑는다`() {
        val taken = generator.generate { false }.value
        var asked = 0

        val result = generator.generate { candidate ->
            asked++
            candidate == taken
        }

        assertTrue(asked >= 1)
        assertTrue(result.value != taken, "충돌한 값을 그대로 돌려줬다: ${result.value}")
    }

    @Test
    fun `상한을 넘도록 계속 충돌하면 숫자를 넓혀 물러난다`() {
        // 무엇을 물어도 사용 중이라고 답한다 — 재시도가 모두 실패하는 경우다.
        val result = generator.generate { true }

        // 가입을 실패시키지 않는다. 물러난 값도 닉네임 규칙은 지킨다.
        assertEquals(result.value, Nickname(result.value).value)
        val digits = result.value.takeLastWhile { it.isDigit() }
        assertTrue(digits.length >= NicknameGenerator.DIGITS, "숫자 구간이 넓어지지 않았다: ${result.value}")
    }

    @Test
    fun `반복 호출이 서로 다른 값을 만든다`() {
        val values = (1..100).map { generator.generate { false }.value }.toSet()
        // 200x100x10000 조합에서 100번 뽑아 전부 같을 확률은 사실상 0이다.
        assertTrue(values.size > 90, "생성값이 지나치게 겹친다: ${values.size}")
    }
}
