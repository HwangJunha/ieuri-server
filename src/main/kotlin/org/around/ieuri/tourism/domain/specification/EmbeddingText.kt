package org.around.ieuri.tourism.domain.specification

/**
 * 장소 임베딩의 입력 텍스트(SPEC-0035 S2). `"{제목} | {카테고리 라벨} | {주소} | {소개문}"`, 행의 언어 그대로.
 * 제목은 저장된 그대로 쓴다 — 면세 태그가 붙은 매장끼리 닮게 나오는 것이 의도다. 주소·소개문이 없으면 빈 칸.
 *
 * 소개문은 [maxOverviewChars]로 절삭한다 — 토큰 비용의 천장이다. 상한 안의 **마지막 문장 끝**에서 자르되,
 * 그 지점이 상한의 절반보다 앞이면(문장이 아주 길면) 상한에서 그대로 자른다.
 */
object EmbeddingText {
    private val SENTENCE_ENDS = charArrayOf('.', '。', '!', '?', '\n')

    fun of(title: String, categoryLabel: String, address: String?, overview: String? = null, maxOverviewChars: Int = 500): String = "$title | $categoryLabel | ${address.orEmpty()} | ${truncateOverview(overview, maxOverviewChars)}".trim()

    fun truncateOverview(overview: String?, maxChars: Int): String {
        val text = overview.orEmpty().trim()
        if (text.length <= maxChars) return text
        val head = text.substring(0, maxChars)
        val lastSentenceEnd = head.lastIndexOfAny(SENTENCE_ENDS)
        return if (lastSentenceEnd >= maxChars / 2) head.substring(0, lastSentenceEnd + 1).trim() else head.trim()
    }
}
