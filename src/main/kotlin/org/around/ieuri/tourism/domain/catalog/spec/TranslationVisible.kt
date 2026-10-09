package org.around.ieuri.tourism.domain.catalog.spec

import org.around.ieuri.tourism.domain.catalog.TranslationPresence
import org.around.ieuri.tourism.domain.shared.Specification

/**
 * 번역이 보이는가 — 그 언어의 원본이 카탈로그에 붙어 있으면 보인다(SPEC-0029 R-6·R-12).
 * - 원본을 붙일 때([org.around.ieuri.tourism.domain.catalog.SpotCatalog.attach])와 매일 일괄 반영([org.around.ieuri.tourism.domain.catalog.TranslationVisibilitySweep])이 같은 규칙을 쓴다.
 */
object TranslationVisible : Specification<TranslationPresence> {
    override fun isSatisfiedBy(candidate: TranslationPresence): Boolean = candidate.language in candidate.originLanguages
}
