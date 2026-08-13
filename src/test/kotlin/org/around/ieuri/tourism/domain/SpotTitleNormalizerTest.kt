package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 제목 정규화·한국어 키 추출 규칙(SPEC-0019 R-2).
 *
 * 전각 괄호 처리가 이 규칙의 핵심이다 — 빠지면 JA 커버리지가 53%로 무너진다.
 */
class SpotTitleNormalizerTest {

    @Nested
    @DisplayName("정규화")
    inner class Normalize {

        @Test
        fun `공백과 구분자를 지운다`() {
            assertEquals("마린시티", SpotTitleNormalizer.normalize("마린 시티"))
            assertEquals("수암골카페거리", SpotTitleNormalizer.normalize("수암골 카페거리"))
            assertEquals("서울역", SpotTitleNormalizer.normalize("서울·역"))
        }

        @Test
        fun `대괄호 태그를 제거한다`() {
            assertEquals("여포꾸이꾸이", SpotTitleNormalizer.normalize("[한국관광 품질인증] 여포꾸이꾸이"))
            assertEquals("여포꾸이꾸이", SpotTitleNormalizer.normalize("[Tax Refund Shop] 여포꾸이꾸이"))
            assertEquals("여포꾸이꾸이", SpotTitleNormalizer.normalize("[事後免税店] 여포꾸이꾸이"))
        }

        @Test
        fun `전각 대괄호도 반각과 같게 처리한다`() {
            assertEquals("여포꾸이꾸이", SpotTitleNormalizer.normalize("［事後免税店］여포꾸이꾸이"))
        }

        @Test
        fun `정규화 결과가 비면 matchKey는 null이다`() {
            // 제목이 태그뿐이면 키가 없다. 빈 문자열을 키로 쓰면 모든 행이 서로 일치한다.
            assertEquals("", SpotTitleNormalizer.normalize("[한국관광 품질인증]"))
            assertEquals(null, SpotTitleNormalizer.matchKey("[한국관광 품질인증]"))
            assertEquals(null, SpotTitleNormalizer.matchKey("   "))
        }

        @Test
        fun `내용이 있으면 matchKey가 정규화 키를 돌려준다`() {
            assertEquals("마린시티", SpotTitleNormalizer.matchKey("마린 시티"))
        }
    }

    @Nested
    @DisplayName("한국어 키 추출")
    inner class ExtractKoreanKeys {

        @Test
        fun `반각 괄호 안의 한국어를 뽑는다`() {
            assertEquals(listOf("마린시티"), SpotTitleNormalizer.extractKoreanKeys("Marine City (마린시티)"))
        }

        @Test
        fun `전각 괄호도 반각과 동일하게 판정한다`() {
            // **이 처리가 없으면 JA 커버리지가 53%로 무너진다.**
            assertEquals(listOf("마린시티"), SpotTitleNormalizer.extractKoreanKeys("マリンシティ（마린시티）"))
        }

        @Test
        fun `중첩 괄호에서 안쪽까지 후보로 본다`() {
            val keys = SpotTitleNormalizer.extractKoreanKeys("Something (Outer (마린시티))")

            assertTrue(keys.contains("마린시티"), "안쪽 괄호의 한국어가 후보에 있어야 한다: $keys")
        }

        @Test
        fun `대괄호 안 한글도 후보로 본다`() {
            val keys = SpotTitleNormalizer.extractKoreanKeys("Lacantina [라칸티나]")

            assertTrue(keys.contains("라칸티나"), "대괄호 안의 한국어가 후보에 있어야 한다: $keys")
        }

        @Test
        fun `슬래시 뒤 한글도 후보로 본다`() {
            val keys = SpotTitleNormalizer.extractKoreanKeys("Seoul Museum / 서울박물관")

            assertTrue(keys.contains("서울박물관"), "슬래시 뒤 한국어가 후보에 있어야 한다: $keys")
        }

        @Test
        fun `한국어가 없으면 빈 목록이다`() {
            // 이것이 NO_KOREAN_KEY 미해결 사유의 판정 근거다.
            assertEquals(emptyList<String>(), SpotTitleNormalizer.extractKoreanKeys("SM Entertainment"))
            assertEquals(emptyList<String>(), SpotTitleNormalizer.extractKoreanKeys("Phil hotel"))
        }

        @Test
        fun `추출한 키는 정규화된 형태다`() {
            // 추출값이 KO 제목의 정규화 키와 대조되므로 같은 규칙을 거쳐야 한다.
            assertEquals(listOf("마린시티"), SpotTitleNormalizer.extractKoreanKeys("Marine City (마린 시티)"))
        }

        @Test
        fun `여는 괄호 없이 닫는 괄호만 있어도 깨지지 않는다`() {
            // 깨진 제목이 실재한다. 예외 대신 빈 목록이어야 한다.
            assertEquals(emptyList<String>(), SpotTitleNormalizer.extractKoreanKeys("Marine City 마린시티)"))
        }

        @Test
        fun `같은 키가 여러 번 나와도 중복을 제거한다`() {
            val keys = SpotTitleNormalizer.extractKoreanKeys("(마린시티) Marine City (마린시티)")

            assertEquals(listOf("마린시티"), keys)
        }
    }

    @Nested
    @DisplayName("한글 포함 판정")
    inner class ContainsHangul {

        @Test
        fun `한글 음절을 인식한다`() {
            assertTrue(SpotTitleNormalizer.containsHangul("마린시티"))
            assertTrue(SpotTitleNormalizer.containsHangul("Marine 시티"))
        }

        @Test
        fun `한자와 가나는 한글이 아니다`() {
            // ZH·JA 제목이 한국어 키로 오인되면 안 된다.
            assertFalse(SpotTitleNormalizer.containsHangul("釜山"))
            assertFalse(SpotTitleNormalizer.containsHangul("マリンシティ"))
            assertFalse(SpotTitleNormalizer.containsHangul("Marine City"))
        }
    }
}
