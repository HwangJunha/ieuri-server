package org.around.ieuri.tourism.domain.spot.spec

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Specification

/**
 * 비관광 spot 제외 판정(SPEC-0018). 제목과 카테고리만 보는 순수 판정이다.
 * - 보호 카테고리가 아니고 ∧ 지점명 보호에 걸리지 않고 ∧ 키워드에 일치하면 제외한다.
 * - 키워드 목록이 비면 항상 false다(R12).
 */
class SpotExclusionRule(
    /** 제외 키워드. 영문(ASCII 문자로 시작·끝)은 단어 경계로, 한글은 부분 일치로 본다. */
    keywords: List<String>,
    /** 지점명 보호 패턴. 하나라도 제목에 들어 있으면 제외하지 않는다. */
    branchNamePatterns: List<String>,
    /** 키워드에 걸려도 제외하지 않는 카테고리. */
    private val protectedCategories: Set<SpotCategory> = PROTECTED_CATEGORIES,
) : Specification<SpotExclusionCandidate> {

    /** 빈 문자열·공백만인 항목은 버린다 — 남기면 모든 제목에 일치한다. */
    private val matchers: List<Regex> = keywords
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::toMatcher)

    private val protections: List<String> = branchNamePatterns
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private val protectedCategory = Specification<SpotExclusionCandidate> { it.category in protectedCategories }

    private val branchName = Specification<SpotExclusionCandidate> { candidate ->
        protections.any { candidate.title.contains(it, ignoreCase = true) }
    }

    private val keywordHit = Specification<SpotExclusionCandidate> { candidate -> matchers.any { it.containsMatchIn(candidate.title) } }

    private val rule = !protectedCategory and !branchName and keywordHit

    override fun isSatisfiedBy(candidate: SpotExclusionCandidate): Boolean = rule.isSatisfiedBy(candidate)

    private companion object {
        /** 관광 카테고리는 키워드에 걸려도 보호한다(R5). */
        val PROTECTED_CATEGORIES = setOf(SpotCategory.TOURIST_SPOT, SpotCategory("CULTURAL_FACILITY"))

        /**
         * 키워드를 정규식으로. 영문 키워드만 단어 경계를 두르고, 한글은 부분 일치한다.
         * - 대소문자는 구분하지 않는다.
         */
        fun toMatcher(keyword: String): Regex {
            val quoted = Regex.escape(keyword)
            val prefix = if (keyword.first().isAsciiLetterOrDigit()) "\\b" else ""
            val suffix = if (keyword.last().isAsciiLetterOrDigit()) "\\b" else ""
            return Regex("$prefix$quoted$suffix", RegexOption.IGNORE_CASE)
        }

        /** `\b`는 ASCII 단어 문자에서만 의미가 있다 — 한글 옆의 경계는 문맥에 따라 뒤집힌다. */
        fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    }
}
