package org.around.ieuri.tourism.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 S2 — 임베딩 입력 텍스트. 제목은 저장된 그대로(면세 태그 포함), 소개문은 문장 끝에서 절삭. */
class EmbeddingTextTest {

    @Test
    fun `제목·라벨·주소·소개문을 구분자로 잇는다`() {
        assertEquals(
            "[Tax Refund Shop] 나이키 | 쇼핑 | 서울 금천구 | 스포츠 브랜드 매장이다.",
            EmbeddingText.of("[Tax Refund Shop] 나이키", "쇼핑", "서울 금천구", "스포츠 브랜드 매장이다.", 500),
        )
    }

    @Test
    fun `주소·소개문이 없으면 빈 칸이고 끝 공백은 없다`() {
        assertEquals("가산 칼국수 | 음식점 |  |", EmbeddingText.of("가산 칼국수", "음식점", null, null, 500))
    }

    @Test
    fun `상한 안에 문장 끝이 있으면 거기서 자른다`() {
        val overview = "첫 문장이다. 둘째 문장이다. 셋째 문장은 아주 길어서 잘려 나간다"
        assertEquals("첫 문장이다. 둘째 문장이다.", EmbeddingText.truncateOverview(overview, 20))
    }

    @Test
    fun `문장 끝이 상한의 절반보다 앞이면 상한에서 그대로 자른다`() {
        val overview = "짧다. " + "가".repeat(100)
        assertEquals(("짧다. " + "가".repeat(100)).substring(0, 30).trim(), EmbeddingText.truncateOverview(overview, 30))
    }

    @Test
    fun `상한 이하면 그대로다`() {
        assertEquals("그대로.", EmbeddingText.truncateOverview("그대로.", 500))
        assertEquals("", EmbeddingText.truncateOverview(null, 500))
    }
}
