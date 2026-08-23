package org.around.ieuri.tourism.domain.specification

import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 비관광 spot 제외 판정(SPEC-0018). 제목과 카테고리만 보는 순수 함수다.
 *
 * 키워드·보호 패턴은 주입받는다 — 도메인은 설정 메커니즘을 알지 않는다.
 *
 * 판정 순서가 곧 우선순위다: 카테고리 보호 → 지점명 보호 → 키워드 일치.
 * 앞선 보호가 걸리면 키워드를 보지 않는다.
 */
class SpotExclusionRule(
    /** 제외 키워드. 영문(ASCII 문자로 시작·끝)은 단어 경계로, 한글은 부분 일치로 본다. */
    keywords: List<String>,
    /** 지점명 보호 패턴. 하나라도 제목에 들어 있으면 제외하지 않는다. */
    branchNamePatterns: List<String>,
    /** 키워드에 걸려도 제외하지 않는 카테고리. */
    private val protectedCategories: Set<SpotCategory> = PROTECTED_CATEGORIES,
) {

    /** 빈 문자열·공백만인 항목은 버린다 — 남겨 두면 모든 제목에 일치해 전량 비활성이 된다. */
    private val matchers: List<Regex> = keywords
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::toMatcher)

    private val protections: List<String> = branchNamePatterns
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    /**
     * 이 제목·카테고리가 제외 대상인가.
     *
     * **키워드 목록이 비면 항상 false다**(R12) — 빈 목록을 "전부 제외"로 읽으면
     * 설정 실수 한 번이 전 행을 비활성으로 만든다.
     */
    fun isExcluded(title: String, category: SpotCategory): Boolean {
        if (matchers.isEmpty()) return false
        if (category in protectedCategories) return false
        if (protections.any { title.contains(it, ignoreCase = true) }) return false
        return matchers.any { it.containsMatchIn(title) }
    }

    private companion object {
        /**
         * 관광 카테고리는 키워드에 걸려도 보호한다(R5) — "서울 대한의원"(사적),
         * "서울대학교병원의학박물관"처럼 업태명을 가진 관광지가 실재한다.
         */
        val PROTECTED_CATEGORIES = setOf(SpotCategory.TOURIST_SPOT, SpotCategory("CULTURAL_FACILITY"))

        /**
         * 키워드를 정규식으로. **영문 키워드만 단어 경계를 두른다.**
         *
         * 경계가 없으면 `CU`가 "인사동 문화의 거리"·"판문점(Joint Se**cu**rity Area)"까지 잡는다
         * (실측 표본에서 52건 → 262건). 한글은 교착어라 `명동명약국`처럼 접사 없이 붙으므로
         * 경계를 두면 오히려 놓친다.
         *
         * 대소문자는 구분하지 않는다 — `emart24`·`E-Mart 24` 표기가 혼재한다.
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
