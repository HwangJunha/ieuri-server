package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.domain.catalog.spec.DistrictUnassigned
import org.around.ieuri.tourism.domain.catalog.spec.TranslationVisible
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select

/** [TranslationVisible]을 번역 행(`spot_catalog_translation`) 조건으로 옮긴다: 그 언어의 원본이 이 카탈로그에 붙어 있다. */
fun TranslationVisible.toCondition(): Op<Boolean> = exists(
    SpotTable.select(SpotTable.id).where {
        (SpotTable.catalogId eq SpotCatalogTranslationTable.catalogId) and (SpotTable.language eq SpotCatalogTranslationTable.language)
    },
)

/** [DistrictUnassigned]를 카탈로그 행(`spot_catalog`) 조건으로 옮긴다: 시군구가 비어 있다. */
fun DistrictUnassigned.toCondition(): Op<Boolean> = SpotCatalogTable.districtCode.isNull()
