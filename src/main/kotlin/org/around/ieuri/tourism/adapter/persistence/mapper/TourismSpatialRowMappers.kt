package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.CongestionAreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellDistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.required.MapCandidateView
import org.around.ieuri.tourism.application.footprint.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.region.provided.dto.RegionRow
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.footprint.MapCellDistrictRow
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow

// 공간 조회 행 → 읽기 모델. geography 좌표는 쿼리가 `ST_Y`(위도)·`ST_X`(경도)로 읽어 이름 붙은 인자로 넘긴다.

fun ResultRow.toCongestionAreaName(): AreaName = AreaName(
    ko = this[CongestionAreaTable.nameKo],
    en = this[CongestionAreaTable.nameEn],
    ja = this[CongestionAreaTable.nameJa],
    zh = this[CongestionAreaTable.nameZh],
)

/** 칸 ↔ 시군구 한 행. 칸 이름은 칸에서, 시도 이름은 시군구의 시도에서 온다. 좌표는 시군구 중심이다. */
fun ResultRow.toMapCellDistrictRow(latitude: Double, longitude: Double): MapCellDistrictRow = MapCellDistrictRow(
    cellId = this[MapCellDistrictTable.cellId],
    districtCode = this[DistrictTable.code],
    cellName = AreaName(ko = this[MapCellTable.nameKo], en = this[MapCellTable.nameEn], ja = this[MapCellTable.nameJa], zh = this[MapCellTable.nameZh]),
    provinceName = toProvinceName(),
    provinceCode = this[DistrictTable.areaCd],
    center = Coordinates(latitude = latitude, longitude = longitude),
)

/** 추천 후보 시군구 한 행. 좌표는 중심이 아니라 대표 위치다. */
fun ResultRow.toRegionRow(latitude: Double, longitude: Double): RegionRow = RegionRow(
    code = this[DistrictTable.code],
    provinceCode = this[DistrictTable.areaCd],
    name = AreaName(ko = this[DistrictTable.nameKo], en = this[DistrictTable.nameEn], ja = this[DistrictTable.nameJa], zh = this[DistrictTable.nameZh]),
    provinceName = toProvinceName(),
    center = Coordinates(latitude = latitude, longitude = longitude),
)

/** 칸의 장소 한 건. 제목만 번역에서, 나머지는 카탈로그 표준값에서 온다. */
fun ResultRow.toCellSpotView(likeCount: Long): CellSpotView = CellSpotView(
    id = this[SpotCatalogTable.id],
    title = this[SpotCatalogTranslationTable.title],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    categoryCode = this[SpotCatalogTable.category],
    coordinates = Coordinates(latitude = this[SpotCatalogTable.mapY], longitude = this[SpotCatalogTable.mapX]),
    likeCount = likeCount,
)

/** 지도 마커 한 건. id는 카탈로그 id다. */
fun ResultRow.toMapSpotView(): MapSpotView = MapSpotView(
    id = this[SpotCatalogTable.id],
    title = this[SpotCatalogTranslationTable.title],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    category = SpotCategory(this[SpotCatalogTable.category]),
    lng = this[SpotCatalogTable.mapX],
    lat = this[SpotCatalogTable.mapY],
)

fun ResultRow.toMapCandidateView(distanceMeters: Double): MapCandidateView = MapCandidateView(
    id = this[SpotCatalogTable.id],
    title = this[SpotCatalogTranslationTable.title],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    category = SpotCategory(this[SpotCatalogTable.category]),
    lng = this[SpotCatalogTable.mapX],
    lat = this[SpotCatalogTable.mapY],
    distanceMeters = distanceMeters,
)

/** 반경 조회 한 건. 번역 열은 거리순으로 자른 [ranked]에서, 썸네일·저작권만 카탈로그에서 온다. id는 카탈로그 id다. */
fun ResultRow.toSpotView(ranked: QueryAlias, distanceMeters: Double): SpotView = SpotView(
    id = this[ranked[SpotCatalogTranslationTable.catalogId]],
    title = this[ranked[SpotCatalogTranslationTable.title]],
    address = this[ranked[SpotCatalogTranslationTable.address]],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    mapX = this[ranked[SpotCatalogTranslationTable.mapX]],
    mapY = this[ranked[SpotCatalogTranslationTable.mapY]],
    category = SpotCategory(this[ranked[SpotCatalogTranslationTable.category]]),
    copyright = CopyrightType.valueOf(this[SpotCatalogTable.copyright]),
    distanceMeters = distanceMeters,
)

private fun ResultRow.toProvinceName(): AreaName = AreaName(ko = this[AreaTable.nameKo], en = this[AreaTable.nameEn], ja = this[AreaTable.nameJa], zh = this[AreaTable.nameZh])
