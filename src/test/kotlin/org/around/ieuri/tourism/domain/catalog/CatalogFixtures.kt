package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale

internal const val THUMB = "https://img/1.jpg"
internal const val MAP_X = 129.1234
internal const val MAP_Y = 35.5678

internal fun origin(
    spotId: Long = 11,
    language: DomainLocale = DomainLocale.EN,
    title: String = "Marine City (마린시티)",
    address: String? = "Busan",
    thumbnailImageUrl: String = THUMB,
    mapX: Double = MAP_X,
    mapY: Double = MAP_Y,
) = CatalogOrigin(
    spotId = spotId,
    language = language,
    title = title,
    address = address,
    thumbnailImageUrl = thumbnailImageUrl,
    mapX = mapX,
    mapY = mapY,
    category = SpotCategory.TOURIST_SPOT,
    copyright = CopyrightType.TYPE1,
)

internal fun koreanOrigin(spotId: Long = 12, title: String = "마린시티", mapX: Double = MAP_X, mapY: Double = MAP_Y) = origin(spotId = spotId, language = DomainLocale.KO, title = title, address = "부산", mapX = mapX, mapY = mapY)

internal fun translation(language: DomainLocale, title: String = "마린시티", visible: Boolean = true) = CatalogTranslation(language = language, title = title, address = null, visible = visible)

internal fun catalog(id: Long? = 900, vararg translations: CatalogTranslation = arrayOf(translation(DomainLocale.KO))) = SpotCatalog(
    id = id,
    standard = CatalogStandardValues(MAP_X, MAP_Y, THUMB, SpotCategory.TOURIST_SPOT, CopyrightType.TYPE1),
    translations = translations.toList(),
)
