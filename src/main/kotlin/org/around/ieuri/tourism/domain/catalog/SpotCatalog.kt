package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * 새 장소 하나(SPEC-0019 R-1). 카탈로그를 만들 때의 입력이다 — 표준값과 처음 언어의 표시값을 함께 쓴다.
 * - 이후의 번역 추가·삭제와 축제 기간은 배치가 저장소 연산으로 한다(SPEC-0039 D-74·D-76).
 */
data class SpotCatalog(
    /** 언어 무관 표준값. 조회가 카드에 쓰는 값이다. */
    val standard: CatalogStandardValues,
    /** 처음 언어. 원본이 있는 언어에만 표시값이 생긴다(SPEC-0029 R-6) — 다른 언어는 비어 있다. */
    val language: DomainLocale,
    /** 그 언어의 표시값. */
    val translation: CatalogTranslation,
) {

    companion object {
        /** spot 원본 한 행에서 카탈로그를 만든다. 어느 언어로도 만들 수 있다. */
        fun from(
            language: DomainLocale,
            title: String,
            address: String? = null,
            standard: CatalogStandardValues,
        ): SpotCatalog = SpotCatalog(
            standard = standard,
            language = language,
            translation = CatalogTranslation(title, address),
        )
    }
}

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
        require(mapX in KOREA_LON_RANGE && mapY in KOREA_LAT_RANGE) {
            "좌표가 한국 범위를 벗어났습니다: ($mapX, $mapY)"
        }
    }

    companion object {
        /** 한국 영토를 감싸는 경계. 이 밖의 좌표는 상류 결손이며 카탈로그를 만들지 않는다. */
        val KOREA_LON_RANGE = 124.0..132.0
        val KOREA_LAT_RANGE = 33.0..39.0

        /** 이 좌표로 카탈로그를 만들 수 있는가. 배치가 적재 전에 묻는다. */
        fun isValidCoordinate(mapX: Double, mapY: Double): Boolean = mapX in KOREA_LON_RANGE && mapY in KOREA_LAT_RANGE
    }
}

/**
 * 카탈로그의 한 언어 표시값 — 제목과 주소. 이 언어에 원본이 존재한다는 증거다(SPEC-0029 R-6).
 * - 값은 전부 업스트림 원문이다. [address]는 원문에 없으면 null이다.
 */
data class CatalogTranslation(
    val title: String,
    val address: String? = null,
) {
    init {
        require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
    }
}
