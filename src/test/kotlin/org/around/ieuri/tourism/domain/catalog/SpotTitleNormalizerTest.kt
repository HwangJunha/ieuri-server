package org.around.ieuri.tourism.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** 제목 정규화·한국어 키 추출 규칙(SPEC-0019 R-2). */
class SpotTitleNormalizerTest {

    @Nested
    @DisplayName("매칭 키 — 한글만 남긴다")
    inner class MatchKey {

        @Test
        fun `한글이 아닌 것은 전부 버린다`() {
            // 규칙이 이것 하나뿐이라 표기 변형을 따로 다루지 않는다.
            assertEquals("마린시티", SpotTitleNormalizer.matchKey("마린 시티"))
            assertEquals("마린시티", SpotTitleNormalizer.matchKey("Marine City (마린시티)"))
            assertEquals("마린시티", SpotTitleNormalizer.matchKey("マリンシティ（마린시티）"))
            assertEquals("서울역", SpotTitleNormalizer.matchKey("서울·역"))
        }

        @Test
        fun `괄호 안팎과 슬래시를 가리지 않는다`() {
            // 회귀: 괄호 안 슬래시 뒤만 뽑아 `용인`이 키가 되던 결함.
            val key = SpotTitleNormalizer.lookupKey("韓華渡假村/龍仁(한화리조트 / 용인)")
            assertEquals("한화리조트용인", key)
        }

        @Test
        fun `영문과 숫자가 한글 사이에 껴 있어도 버린다`() {
            assertEquals("제주알호텔", SpotTitleNormalizer.lookupKey("JEJU R HOTEL (제주알(R)호텔)"))
            assertEquals("케이티엔지상상마당춘천스테이", SpotTitleNormalizer.lookupKey("KT&G (케이티엔지(KT&G) 상상마당 춘천 스테이)"))
        }

        @Test
        fun `한글이 없으면 null이다`() {
            assertEquals(null, SpotTitleNormalizer.matchKey("SM Entertainment"))
            assertEquals(null, SpotTitleNormalizer.matchKey("   "))
            assertEquals(null, SpotTitleNormalizer.lookupKey("Phil hotel"))
        }

        @Test
        fun `인증 태그의 한글은 키에 섞이지 않는다`() {
            // 회귀: 태그 한글이 키에 들어가 무관한 장소가 한 카탈로그로 뭉쳤다.
            assertEquals("생각속의집", SpotTitleNormalizer.lookupKey("House of Mind [Korea Quality]생각속의집[한국관광 품질인증]"))
            assertEquals("죽헌고택", SpotTitleNormalizer.lookupKey("Jukheon House [Korea Quality] / 죽헌고택 [한국관광 품질인증]"))
            assertEquals("종묘", SpotTitleNormalizer.lookupKey("宗廟 [ユネスコ世界遺産]（종묘 [유네스코 세계문화유산]）"))
        }

        @Test
        fun `태그뿐인 제목은 키가 없다`() {
            assertEquals(null, SpotTitleNormalizer.lookupKey("[한국관광 품질인증]"))
        }
    }

    @Nested
    @DisplayName("표시용 제목 — 한글을 지운다")
    inner class DisplayTitle {

        @Test
        fun `병기된 한국어를 떼어낸다`() {
            assertEquals("Marine City", SpotTitleNormalizer.displayTitle("Marine City (마린시티)"))
            assertEquals("マリンシティ", SpotTitleNormalizer.displayTitle("マリンシティ（마린시티）"))
            assertEquals("海洋城市", SpotTitleNormalizer.displayTitle("海洋城市(마린시티)"))
        }

        @Test
        fun `괄호를 안 써도 한글이면 지운다`() {
            // 구분자 없이 붙은 형태.
            assertEquals("Hue Hotel", SpotTitleNormalizer.displayTitle("Hue Hotel 휴호텔"))
            assertEquals("Line Hotel", SpotTitleNormalizer.displayTitle("Line Hotel / 라인호텔"))
        }

        @Test
        fun `대괄호 태그의 한글도 지운다`() {
            assertEquals("Today [Korea Quality]", SpotTitleNormalizer.displayTitle("Today [Korea Quality]오늘[한국관광 품질인증]"))
        }

        @Test
        fun `한글이 빠진 뒤 남는 빈 괄호를 정리한다`() {
            // 중첩은 안쪽부터 푼다 — 한 번만 돌리면 `Hotel The H ( (Hotel The H))`가 남는다.
            assertEquals("Hotel The H", SpotTitleNormalizer.displayTitle("Hotel The H (호텔 더 에이치(Hotel The H))"))
            assertEquals("JEJU R HOTEL", SpotTitleNormalizer.displayTitle("JEJU R HOTEL (제주알(R)호텔)"))
        }

        @Test
        fun `한글이 없는 괄호는 원문의 일부이므로 남긴다`() {
            assertEquals("Gyeongbokgung (Palace)", SpotTitleNormalizer.displayTitle("Gyeongbokgung (Palace)"))
            assertEquals("Terminal A / B", SpotTitleNormalizer.displayTitle("Terminal A / B"))
            assertEquals(
                "Seoul Hanok Stay (JNP Stay)",
                SpotTitleNormalizer.displayTitle("Seoul Hanok Stay (JNP Stay) (서울한옥스테이(jnpstay))"),
            )
        }

        @Test
        fun `한글만으로 이루어진 제목은 null이다`() {
            // TourAPI가 번역 대신 원문을 넣은 것이라 그 언어의 제목이 아니다.
            assertNull(SpotTitleNormalizer.displayTitle("초려공방"))
            assertNull(SpotTitleNormalizer.displayTitle("(마린시티)"))
            assertNull(SpotTitleNormalizer.displayTitle("마린시티"))
        }

        @Test
        fun `짝이 맞지 않는 괄호가 있어도 한글은 지운다`() {
            // 깨진 원문이 실재한다. 괄호 짝을 따지지 않으므로 한글은 그대로 걷힌다.
            assertEquals(
                "MADRID Business Hotel Gwnagju )",
                SpotTitleNormalizer.displayTitle("MADRID Business Hotel Gwnagju 한성 마드리드 광주호텔)"),
            )
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
            assertFalse(SpotTitleNormalizer.containsHangul("釜山"))
            assertFalse(SpotTitleNormalizer.containsHangul("マリンシティ"))
            assertFalse(SpotTitleNormalizer.containsHangul("Marine City"))
        }
    }
}
