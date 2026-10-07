package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 카탈로그 분할의 결과([SpotCatalog.split]). [departures]는 떠나는 군집마다 새 카탈로그와 옮겨 갈 원본이다.
 * - [removedLanguages]는 남는 카탈로그에서 지울 번역의 언어다. 감춤이 아니라 삭제다.
 */
data class CatalogSplit(
    val departures: List<Departure>,
    val removedLanguages: List<DomainLocale>,
) {
    data class Departure(
        val catalog: SpotCatalog,
        val spotIds: List<Long>,
    )

    companion object {
        /** 이 수를 **초과**하는 원본을 가진 카탈로그만 분할 후보다. */
        const val MIN_ORIGIN_COUNT = 4

        /** 원본 간 최대 거리가 이 값 **이상**이면 다른 장소로 본다. */
        const val MIN_DISTANCE_METERS = 10_000.0
    }
}
