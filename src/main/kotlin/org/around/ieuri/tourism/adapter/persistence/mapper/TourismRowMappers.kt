package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoDemandIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.catalog.OriginFestivalPeriod
import org.around.ieuri.tourism.domain.catalog.SpotReaction
import org.around.ieuri.tourism.domain.footprint.ShareLink
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

// tourism 행 ↔ 도메인 변환. enum은 이름 문자열로 저장한다. id·생성 시각·`location`은 DB가 채운다.

fun SpotReaction.writeTo(row: UpdateBuilder<*>) {
    row[SpotReactionTable.memberId] = memberId
    row[SpotReactionTable.catalogId] = catalogId
    row[SpotReactionTable.language] = language.name
    row[SpotReactionTable.reaction] = reaction.name
}

fun CatalogStandardValues.writeTo(row: UpdateBuilder<*>) {
    row[SpotCatalogTable.mapX] = mapX
    row[SpotCatalogTable.mapY] = mapY
    row[SpotCatalogTable.thumbnailImageUrl] = thumbnailImageUrl
    row[SpotCatalogTable.category] = category.code
    row[SpotCatalogTable.copyright] = copyright.name
}

/** 번역 한 행. 좌표·분류는 카탈로그 표준값의 사본이다 — 비면 그 카탈로그는 검색에서 사라진다. */
fun CatalogTranslation.writeTo(row: UpdateBuilder<*>, catalogId: Long, standard: CatalogStandardValues) {
    row[SpotCatalogTranslationTable.catalogId] = catalogId
    row[SpotCatalogTranslationTable.language] = language.name
    row[SpotCatalogTranslationTable.title] = title
    row[SpotCatalogTranslationTable.address] = address
    row[SpotCatalogTranslationTable.mapX] = standard.mapX
    row[SpotCatalogTranslationTable.mapY] = standard.mapY
    row[SpotCatalogTranslationTable.category] = standard.category.code
    row[SpotCatalogTranslationTable.visible] = visible
}

fun ResultRow.toCatalogStandardValues(): CatalogStandardValues = CatalogStandardValues(
    mapX = this[SpotCatalogTable.mapX],
    mapY = this[SpotCatalogTable.mapY],
    thumbnailImageUrl = this[SpotCatalogTable.thumbnailImageUrl],
    category = SpotCategory(this[SpotCatalogTable.category]),
    copyright = CopyrightType.valueOf(this[SpotCatalogTable.copyright]),
)

fun ResultRow.toCatalogTranslation(): CatalogTranslation = CatalogTranslation(
    language = DomainLocale.valueOf(this[SpotCatalogTranslationTable.language]),
    title = this[SpotCatalogTranslationTable.title],
    address = this[SpotCatalogTranslationTable.address],
    visible = this[SpotCatalogTranslationTable.visible],
)

/** 원본(`spot`)과 그 축제(`spot_festival`)를 조인한 한 행. */
fun ResultRow.toOriginFestivalPeriod(): OriginFestivalPeriod = OriginFestivalPeriod(
    spotId = this[SpotTable.id],
    language = DomainLocale.valueOf(this[SpotTable.language]),
    period = FestivalPeriod(start = this[SpotFestivalTable.eventStartDate], end = this[SpotFestivalTable.eventEndDate]),
)

/** 원본 한 행. `last_synced_at`은 호출자가 회차 시각으로 쓴다. 좌표는 (경도=map_x, 위도=map_y)다. */
fun Spot.writeTo(row: UpdateBuilder<*>) {
    row[SpotTable.contentId] = contentId
    row[SpotTable.language] = language.name
    row[SpotTable.title] = title
    row[SpotTable.address] = address
    row[SpotTable.thumbnailImageUrl] = thumbnailImageUrl
    row[SpotTable.mapX] = coordinates.longitude
    row[SpotTable.mapY] = coordinates.latitude
    row[SpotTable.category] = category.code
    row[SpotTable.contentTypeId] = contentTypeId
    row[SpotTable.copyright] = copyright.name
}

fun SpotFestival.writeTo(row: UpdateBuilder<*>, spotId: Long) {
    row[SpotFestivalTable.spotId] = spotId
    row[SpotFestivalTable.eventStartDate] = period.start
    row[SpotFestivalTable.eventEndDate] = period.end
    row[SpotFestivalTable.progressType] = progressType
    row[SpotFestivalTable.festivalType] = festivalType
}

fun SpotDetail.writeTo(row: UpdateBuilder<*>, spotId: Long, bodyAbsent: Boolean) {
    row[SpotDetailTable.spotId] = spotId
    row[SpotDetailTable.title] = title
    row[SpotDetailTable.category] = category.code
    row[SpotDetailTable.thumbnailImageUrl] = thumbnailImageUrl
    row[SpotDetailTable.overview] = overview
    row[SpotDetailTable.address] = address
    row[SpotDetailTable.homepage] = homepage
    row[SpotDetailTable.tel] = tel
    row[SpotDetailTable.copyright] = copyright.name
    row[SpotDetailTable.bodyAbsent] = bodyAbsent
}

fun SpotImage.writeTo(row: UpdateBuilder<*>, spotId: Long, displayOrder: Int) {
    row[SpotDetailImageTable.spotId] = spotId
    row[SpotDetailImageTable.displayOrder] = displayOrder
    row[SpotDetailImageTable.imageUrl] = imageUrl
    row[SpotDetailImageTable.thumbnailUrl] = thumbnailUrl
    row[SpotDetailImageTable.caption] = caption
    row[SpotDetailImageTable.copyright] = copyright.name
}

/** 운영 정보 한 행. 값이 없으면(null) 전 컬럼이 비어 "조회했으나 없음"을 남긴다. */
fun SpotIntro?.writeTo(row: UpdateBuilder<*>, spotId: Long, category: SpotCategory) {
    row[SpotDetailIntroTable.spotId] = spotId
    row[SpotDetailIntroTable.category] = category.code
    row[SpotDetailIntroTable.businessHours] = this?.businessHours
    row[SpotDetailIntroTable.closedDays] = this?.closedDays
    row[SpotDetailIntroTable.parking] = this?.parking
    row[SpotDetailIntroTable.inquiryPhone] = this?.inquiryPhone
    row[SpotDetailIntroTable.fee] = this?.fee
    row[SpotDetailIntroTable.petAllowed] = this?.petAllowed
    row[SpotDetailIntroTable.creditCardAccepted] = this?.creditCardAccepted
}

fun SpotInfoItem.writeTo(row: UpdateBuilder<*>, spotId: Long, category: SpotCategory, displayOrder: Int) {
    row[SpotDetailInfoTable.spotId] = spotId
    row[SpotDetailInfoTable.category] = category.code
    row[SpotDetailInfoTable.displayOrder] = displayOrder
    row[SpotDetailInfoTable.name] = name
    row[SpotDetailInfoTable.text] = text
}

/** 적재된 본문 → 도메인 [SpotDetail]. `likeCount`는 서비스가 얹으므로 채우지 않는다. */
fun ResultRow.toSpotDetail(): SpotDetail = SpotDetail(
    title = this[SpotDetailTable.title],
    category = SpotCategory(this[SpotDetailTable.category]),
    thumbnailImageUrl = this[SpotDetailTable.thumbnailImageUrl],
    overview = this[SpotDetailTable.overview],
    address = this[SpotDetailTable.address],
    homepage = this[SpotDetailTable.homepage],
    tel = this[SpotDetailTable.tel],
    copyright = CopyrightType.valueOf(this[SpotDetailTable.copyright]),
)

fun ResultRow.toSpotImage(): SpotImage = SpotImage(
    imageUrl = this[SpotDetailImageTable.imageUrl],
    thumbnailUrl = this[SpotDetailImageTable.thumbnailUrl],
    caption = this[SpotDetailImageTable.caption],
    copyright = CopyrightType.valueOf(this[SpotDetailImageTable.copyright]),
)

fun ResultRow.toSpotIntro(): SpotIntro = SpotIntro(
    businessHours = this[SpotDetailIntroTable.businessHours],
    closedDays = this[SpotDetailIntroTable.closedDays],
    parking = this[SpotDetailIntroTable.parking],
    inquiryPhone = this[SpotDetailIntroTable.inquiryPhone],
    fee = this[SpotDetailIntroTable.fee],
    petAllowed = this[SpotDetailIntroTable.petAllowed],
    creditCardAccepted = this[SpotDetailIntroTable.creditCardAccepted],
)

fun ResultRow.toSpotInfoItem(): SpotInfoItem = SpotInfoItem(name = this[SpotDetailInfoTable.name], text = this[SpotDetailInfoTable.text])

/** 유효한 공유 링크 한 행. */
fun ResultRow.toShareLink(): ShareLink = ShareLink(
    token = this[MapShareLinkTable.token],
    issuedAt = this[MapShareLinkTable.createdAt].toInstant(),
)

fun SyncVisitorCountCommand.writeTo(row: UpdateBuilder<*>) {
    row[KtoVisitorCountTable.districtCode] = districtCode
    row[KtoVisitorCountTable.baseDate] = baseDate
    row[KtoVisitorCountTable.visitorType] = visitorType
    row[KtoVisitorCountTable.touristCount] = touristCount
}

fun SyncConcentrationRateCommand.writeTo(row: UpdateBuilder<*>) {
    row[KtoConcentrationRateTable.districtCode] = districtCode
    row[KtoConcentrationRateTable.touristSpotName] = touristSpotName
    row[KtoConcentrationRateTable.targetDate] = targetDate
    row[KtoConcentrationRateTable.concentrationRate] = concentrationRate
}

/** 체류·소비 강도 공용. 어느 표인지는 [table]이 정한다. */
fun SyncDemandIntensityCommand.writeTo(row: UpdateBuilder<*>, table: KtoDemandIntensityTable) {
    row[table.districtCode] = districtCode
    row[table.baseMonth] = baseMonth
    row[table.indicatorCode] = indicatorCode
    row[table.indicatorValue] = indicatorValue
}
