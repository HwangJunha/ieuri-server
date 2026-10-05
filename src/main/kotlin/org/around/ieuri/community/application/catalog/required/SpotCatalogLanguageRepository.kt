package org.around.ieuri.community.application.catalog.required

import org.around.ieuri.community.domain.catalog.CatalogLanguage

interface SpotCatalogLanguageRepository {
    /** 표 전체를 [entries]로 맞추고 반영한 정본 수를 돌려준다. 주의: 빈 목록이면 표를 전부 지운다. */
    fun replaceAll(entries: List<CatalogLanguage>): Int
}
