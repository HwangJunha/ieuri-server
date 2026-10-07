package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.shared.DomainLocale

/** (카탈로그, 언어) 쌍. 다른 도메인이 자기 사본을 맞추는 근거다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: DomainLocale,
)
