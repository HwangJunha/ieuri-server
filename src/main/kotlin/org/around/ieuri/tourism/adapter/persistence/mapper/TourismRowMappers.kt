package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRecord
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.reaction.SpotReaction
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

// tourism 행 ↔ 도메인·읽기 모델 변환. enum은 이름 문자열로 저장한다.

/** 새 반응 한 행. id·시각은 DB가 채운다. */
fun SpotReaction.writeTo(row: UpdateBuilder<*>) {
    row[SpotReactionTable.memberId] = memberId
    row[SpotReactionTable.catalogId] = catalogId
    row[SpotReactionTable.language] = language.name
    row[SpotReactionTable.reaction] = reaction.name
}

/** 새 카탈로그 한 행(언어 무관 표준값). `location`은 트리거가 채운다. */
fun CatalogStandardValues.writeTo(row: UpdateBuilder<*>) {
    row[SpotCatalogTable.mapX] = mapX
    row[SpotCatalogTable.mapY] = mapY
    row[SpotCatalogTable.thumbnailImageUrl] = thumbnailImageUrl
    row[SpotCatalogTable.category] = category.code
    row[SpotCatalogTable.copyright] = copyright.name
}

/**
 * 새 번역 한 행. 좌표·분류는 카탈로그 표준값의 사본이다 — 비면 그 카탈로그는 검색에서 사라진다.
 * - 그 언어의 spot에서 만드는 중이라 정의상 그 언어에서 해소된다(`visible = true`).
 */
fun CatalogTranslation.writeTo(row: UpdateBuilder<*>, catalogId: Long, language: DomainLocale, standard: CatalogStandardValues) {
    row[SpotCatalogTranslationTable.catalogId] = catalogId
    row[SpotCatalogTranslationTable.language] = language.name
    row[SpotCatalogTranslationTable.title] = title
    row[SpotCatalogTranslationTable.address] = address
    row[SpotCatalogTranslationTable.mapX] = standard.mapX
    row[SpotCatalogTranslationTable.mapY] = standard.mapY
    row[SpotCatalogTranslationTable.category] = standard.category.code
    row[SpotCatalogTranslationTable.visible] = true
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

/** 유효한 공유 링크 한 행. 조회 경로는 유효한 링크만 다뤄 `revokedAt`을 넘기지 않는다. */
fun ResultRow.toMapShareLinkRecord(): MapShareLinkRecord = MapShareLinkRecord(
    memberId = this[MapShareLinkTable.memberId],
    token = this[MapShareLinkTable.token],
    createdAt = this[MapShareLinkTable.createdAt].toInstant(),
)
