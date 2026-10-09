package org.around.ieuri.tourism.domain.spot.spec

import org.around.ieuri.tourism.domain.shared.Specification

/**
 * 면세 태그 장소 판정(SPEC-0035). 후보는 제목이며, 패턴 중 하나라도 들어 있으면(대소문자 무시) 태그 장소다.
 * - 패턴 목록이 비면 항상 false다.
 */
class TaxRefundTitleRule(patterns: List<String>) : Specification<String> {

    private val patterns: List<String> = patterns.map { it.trim() }.filter { it.isNotEmpty() }

    override fun isSatisfiedBy(candidate: String): Boolean = patterns.any { candidate.contains(it, ignoreCase = true) }
}
