package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 카탈로그에 붙는(또는 붙을) 언어별 원본 spot 한 건. 판정에 필요한 값만 담는다. */
data class CatalogOrigin(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    /** 그 언어의 주소. 번역에 함께 심는다(SPEC-0019 R-4). */
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
) {
    /** KO 카탈로그를 찾을 키. 언어를 가리지 않고 제목에서 한글만 남긴다. 한글이 없으면 null. */
    val matchKey: String? get() = SpotTitleNormalizer.lookupKey(title)

    /** 표시용 제목. 비한국어는 병기된 한글을 걷어낸다. null이면 그 언어의 제목이 아니다 — 배정하지 않는다. */
    val displayTitle: String? get() = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title)

    fun standardValues(): CatalogStandardValues = CatalogStandardValues(mapX, mapY, thumbnailImageUrl, category, copyright)

    /** 이 원본이 만드는 번역. 표시용 제목이 없으면 원문 제목을 쓴다(분할 경로). */
    fun toTranslation(): CatalogTranslation = CatalogTranslation(language, displayTitle ?: title, address, visible = true)
}
