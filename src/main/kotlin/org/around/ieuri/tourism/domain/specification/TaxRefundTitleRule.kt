package org.around.ieuri.tourism.domain.specification

/**
 * 면세 태그 장소 판정(SPEC-0035 R11). 제목 문자열에 설정된 패턴 중 하나라도 들어 있으면 태그 장소다.
 *
 * 패턴은 주입받는다 — 언어별 태그 표기가 다르고(`[Tax Refund Shop]`·`[事後免税店]`·`[事後免稅店]`)
 * 데이터로 늘어날 수 있어 코드가 아니라 설정이다. **패턴 목록이 비면 항상 false다** —
 * 감점을 끄는 뜻이지 전부 태그라는 뜻이 아니다.
 */
class TaxRefundTitleRule(patterns: List<String>) {

    private val patterns: List<String> = patterns.map { it.trim() }.filter { it.isNotEmpty() }

    fun matches(title: String): Boolean = patterns.any { title.contains(it, ignoreCase = true) }
}
