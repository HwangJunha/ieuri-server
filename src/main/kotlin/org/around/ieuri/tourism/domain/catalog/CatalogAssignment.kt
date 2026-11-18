package org.around.ieuri.tourism.domain.catalog

/** 원본 하나의 배정 판정. */
sealed interface CatalogAssignment {
    /** 기존 카탈로그에 붙는다. */
    data class Join(val catalogId: Long) : CatalogAssignment

    /** 새 카탈로그를 만든다. */
    data class Found(val catalog: SpotCatalog) : CatalogAssignment

    /** 표시할 제목이 없어 미배정으로 남긴다. */
    data object Skip : CatalogAssignment
}
