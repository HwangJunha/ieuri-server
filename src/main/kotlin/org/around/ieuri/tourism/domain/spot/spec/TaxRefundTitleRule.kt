package org.around.ieuri.tourism.domain.spot.spec

/**
 * 면세 태그 장소 판정(SPEC-0035). 제목에 패턴 중 하나라도 들어 있으면(대소문자 무시) 태그 장소다.
 * - 패턴 목록이 비면 항상 false다.
 */
class TaxRefundTitleRule(patterns: List<String>) {

    private val patterns: List<String> = patterns.map { it.trim() }.filter { it.isNotEmpty() }

    fun matches(title: String): Boolean = patterns.any { title.contains(it, ignoreCase = true) }
}
