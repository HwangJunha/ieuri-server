package org.around.ieuri.tourism.domain.specification

import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 S2 — 임베딩 입력 텍스트. 제목은 저장된 그대로(면세 태그 포함). */
class EmbeddingTextTest {

    @Test
    fun `제목·라벨·주소를 구분자로 잇는다`() {
        assertEquals("[Tax Refund Shop] 나이키 | 쇼핑 | 서울 금천구", EmbeddingText.of("[Tax Refund Shop] 나이키", "쇼핑", "서울 금천구"))
    }

    @Test
    fun `주소가 없으면 빈 칸이고 끝 공백은 없다`() {
        assertEquals("가산 칼국수 | 음식점 |", EmbeddingText.of("가산 칼국수", "음식점", null))
    }
}
