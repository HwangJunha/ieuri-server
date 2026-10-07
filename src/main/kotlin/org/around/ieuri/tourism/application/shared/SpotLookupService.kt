package org.around.ieuri.tourism.application.shared

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.required.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.shared.provided.SpotQueryUseCase
import org.around.ieuri.tourism.application.shared.provided.dto.AppCategoryCode
import org.around.ieuri.tourism.application.shared.provided.dto.SpotSummaryView
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 다른 도메인용 조회 구현. 카탈로그 행과 카테고리 구성만 읽고, 캐시도 upstream도 타지 않는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotLookupService(
    private val spotQueryPort: SpotQueryPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
) : SpotQueryUseCase {

    override fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode> = appCategoryQueryPort.findCategories(appKey, DomainLocale.fromLanguageTag(languageCode))
        .map { AppCategoryCode(category = it.category.code, label = it.label, iconUrl = it.iconUrl) }

    /** 카탈로그 한 행을 읽는다. [languageCode]에서 해소되지 않으면 null이다 — 다른 언어로 대신하지 않는다. */
    override fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView? = spotQueryPort.findSpotBasics(catalogId, DomainLocale.fromLanguageTag(languageCode))?.toSummary(catalogId)

    /** 카탈로그 여러 행을 읽는다. [languageCode]에서 해소되지 않는 id는 결과에 키가 없다(SPEC-0029 R-24). */
    override fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView> = spotQueryPort.findSpotBasicsIn(catalogIds, DomainLocale.fromLanguageTag(languageCode))
        .mapValues { (catalogId, basics) -> basics.toSummary(catalogId) }

    private fun SpotBasics.toSummary(catalogId: Long) = SpotSummaryView(
        id = catalogId,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        categoryCode = category.code,
        longitude = longitude,
        latitude = latitude,
    )
}
