package org.around.ieuri.tourism.domain.specification

/**
 * 장소 임베딩의 입력 텍스트(SPEC-0035 S2). `"{제목} | {카테고리 라벨} | {주소}"`, 행의 언어 그대로.
 * 제목은 저장된 그대로 쓴다 — 면세 태그가 붙은 매장끼리 닮게 나오는 것이 의도다. 주소가 없으면 빈 칸.
 */
object EmbeddingText {
    fun of(title: String, categoryLabel: String, address: String?): String =
        "$title | $categoryLabel | ${address.orEmpty()}".trim()
}
