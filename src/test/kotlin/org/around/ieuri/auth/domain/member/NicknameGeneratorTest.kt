package org.around.ieuri.auth.domain.member
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.NicknameGenerator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 가입 시 닉네임 자동 생성(SPEC-0011 R20).
 * - 형식(형용사+명사+숫자), 조합 수 100만 이상, 반복 호출이 서로 다른 값을 낸다는 것을 고정한다.
 * - 고유성(중복 재시도)은 계약이 아니다(SPEC-0032 R31).
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
            val value = generator.generate().value
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
            val value = generator.generate().value
            assertEquals(value, Nickname(value).value)
        }
    }

    @Test
    fun `반복 호출이 서로 다른 값을 만든다`() {
        val values = (1..100).map { generator.generate().value }.toSet()
        // 200x100x10000 조합에서 100번 뽑아 전부 같을 확률은 사실상 0이다.
        assertTrue(values.size > 90, "생성값이 지나치게 겹친다: ${values.size}")
    }
}
