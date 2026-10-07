package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 번역 노출 규칙(SPEC-0029 R-6): 그 언어의 원본이 카탈로그에 붙어 있으면 노출한다. 원본이 사라지면 번역은 지우지 않고 감춘다.
 * - 원본을 붙이는 단건 경로([SpotCatalog.attach])와 배치의 일괄 갱신이 이 정의 하나를 쓴다 — 일괄 SQL은 이 규칙의 번역이다(SPEC-0039 D-101).
 */
object TranslationVisibility {
    /** [originLanguages]는 이 카탈로그에 붙은 원본의 언어들이다. */
    fun isSatisfiedBy(language: DomainLocale, originLanguages: Set<DomainLocale>): Boolean = language in originLanguages
}
