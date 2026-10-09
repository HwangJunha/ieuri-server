package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 번역 하나와, 그 카탈로그에 붙은 원본들의 언어. [org.around.ieuri.tourism.domain.catalog.spec.TranslationVisible]의 후보다. */
data class TranslationPresence(
    val language: DomainLocale,
    val originLanguages: Set<DomainLocale>,
)
