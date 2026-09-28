package org.around.ieuri.tourism.domain.event

/**
 * 장소 카탈로그 삭제(SPEC-0023). 다른 컨텍스트가 자신의 참조를 정리하도록 알린다.
 * - 현재 이 이벤트를 발행하는 코드는 없다. 카탈로그는 삭제가 아니라 숨김으로 운영된다.
 */
data class SpotCatalogDeleted(
    val catalogIds: List<Long>,
)
