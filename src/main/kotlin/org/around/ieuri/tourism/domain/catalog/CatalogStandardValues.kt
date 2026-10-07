package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * 카탈로그의 언어 무관 표준값 — 조회가 카드에 쓰는 값이다.
 * - 전부 필수다. 한국 밖 좌표는 거절하며 배치가 적재 단계에서 제외한다.
 */
data class CatalogStandardValues(
    val mapX: Double,
    val mapY: Double,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val copyright: CopyrightType,
) {
    init {
        require(isValidCoordinate(mapX, mapY)) { "좌표가 한국 범위를 벗어났습니다: ($mapX, $mapY)" }
    }

    companion object {
        /** 한국 영토를 감싸는 경계. 이 밖의 좌표는 상류 결손이며 카탈로그를 만들지 않는다. */
        val KOREA_LON_RANGE = 124.0..132.0
        val KOREA_LAT_RANGE = 33.0..39.0

        /** 이 좌표로 카탈로그를 만들 수 있는가. 배치가 적재 전에 묻는다. */
        fun isValidCoordinate(mapX: Double, mapY: Double): Boolean = mapX in KOREA_LON_RANGE && mapY in KOREA_LAT_RANGE
    }
}
