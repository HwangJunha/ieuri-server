package org.around.ieuri.tourism.domain.catalog

/** 원본이 어느 카탈로그에 속하는가(`spot.catalog_id`). 카탈로그 루트([SpotCatalog.admit]·[SpotCatalog.admitAll])가 만든다. */
sealed interface CatalogMembership {
    val catalogId: Long

    /** 미배정 원본 하나를 붙인다. 이미 다른 카탈로그에 붙은 원본은 그대로 둔다 — 덮으면 좋아요가 다른 장소로 옮겨 간다. */
    data class Join(override val catalogId: Long, val spotId: Long) : CatalogMembership

    /** 분할로 갈라져 나온 원본들을 새 카탈로그로 옮긴다. 기존 소속을 덮어쓴다. */
    data class Move(override val catalogId: Long, val spotIds: List<Long>) : CatalogMembership
}
