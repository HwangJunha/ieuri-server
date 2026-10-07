package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.CategoryTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.required.dto.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.dto.SpotIdentity
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.footprint.provided.dto.CellImageView
import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion
import org.around.ieuri.tourism.application.spot.provided.dto.PrefetchTarget
import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.footprint.CellImageKind
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.ResultRow

// tourism 조회 행 → 읽기 모델·조회 결과. 좌표는 경도가 map_x, 위도가 map_y다.

/** 카드 기본값. 제목·주소는 번역에서, 좌표·썸네일·분류는 카탈로그 표준값에서 온다. */
fun ResultRow.toSpotBasics(): SpotBasics = SpotBasics(
    title = this[SpotCatalogTranslationTable.title],
    address = this[SpotCatalogTranslationTable.address],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    category = SpotCategory(this[SpotCatalogTable.category]),
    copyright = CopyrightType.valueOf(this[SpotCatalogTable.copyright]),
    longitude = this[SpotCatalogTable.mapX],
    latitude = this[SpotCatalogTable.mapY],
)

/** upstream 식별 정보. 좌표는 카탈로그에서 읽는다(SPEC-0027). */
fun ResultRow.toSpotIdentity(): SpotIdentity = SpotIdentity(
    spotId = this[SpotTable.id],
    contentId = this[SpotTable.contentId],
    mapX = this[SpotCatalogTable.mapX],
    mapY = this[SpotCatalogTable.mapY],
)

/** 좋아요한 장소 한 건. 클라이언트에 나가는 id는 카탈로그 id이고, 출처 문구는 저작권 유형에서 만든다. */
fun ResultRow.toLikedSpotView(): LikedSpotView {
    val copyright = CopyrightType.valueOf(this[SpotCatalogTable.copyright])
    val catalogId = this[SpotCatalogTable.id]
    return LikedSpotView(
        id = catalogId,
        title = this[SpotCatalogTranslationTable.title],
        thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
        address = this[SpotCatalogTranslationTable.address],
        category = SpotCategory(this[SpotCatalogTable.category]),
        copyright = copyright,
        likedAt = this[SpotReactionTable.updatedAt].toInstant(),
        catalogId = catalogId,
    )
}

fun ResultRow.toCatalogOrigin(): CatalogOrigin = CatalogOrigin(
    spotId = this[SpotTable.id],
    language = DomainLocale.valueOf(this[SpotTable.language]),
    title = this[SpotTable.title],
    address = this[SpotTable.address],
    thumbnailImageUrl = this[SpotTable.thumbnailImageUrl],
    mapX = this[SpotTable.mapX],
    mapY = this[SpotTable.mapY],
    category = SpotCategory(this[SpotTable.category]),
    copyright = CopyrightType.valueOf(this[SpotTable.copyright]),
)

fun ResultRow.toCatalogCandidate(): CatalogCandidate = CatalogCandidate(
    catalogId = this[SpotCatalogTable.id],
    mapX = this[SpotCatalogTable.mapX],
    mapY = this[SpotCatalogTable.mapY],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
)

fun ResultRow.toPrefetchTarget(): PrefetchTarget = PrefetchTarget(
    spotId = this[SpotTable.id],
    contentId = this[SpotTable.contentId],
    language = DomainLocale.valueOf(this[SpotTable.language]),
    category = SpotCategory(this[SpotTable.category]),
    title = this[SpotTable.title],
    thumbnailImageUrl = this[SpotTable.thumbnailImageUrl],
    copyright = CopyrightType.valueOf(this[SpotTable.copyright]),
    address = this[SpotTable.address],
)

/** 카테고리 표시 정보. 라벨은 좌측 조인이라 번역이 없으면 코드가 라벨이다. */
fun ResultRow.toCategoryDisplay(): CategoryDisplay {
    val code = this[CategoryTable.code]
    return CategoryDisplay(
        category = SpotCategory(code),
        label = getOrNull(CategoryTranslationTable.label) ?: code,
        iconUrl = this[CategoryTable.iconUrl],
    )
}

/** 시군구 코드와 그 시도 코드. 둘 다 문자열이라 뒤바뀌기 쉽다. */
fun ResultRow.toDistrictRegion(): DistrictRegion = DistrictRegion(code = this[DistrictTable.code], provinceCode = this[DistrictTable.areaCd])

/** 칸 대표 이미지. `attributionText`는 [CopyrightType]에서 파생된다. */
fun ResultRow.toCellImageView(): CellImageView {
    val copyright = CopyrightType.valueOf(this[MapCellImageTable.copyright])
    return CellImageView(
        imageUrl = this[MapCellImageTable.imageUrl],
        thumbnailUrl = this[MapCellImageTable.thumbnailUrl],
        caption = this[MapCellImageTable.caption],
        kind = CellImageKind.valueOf(this[MapCellImageTable.kind]),
        copyright = copyright,
    )
}
