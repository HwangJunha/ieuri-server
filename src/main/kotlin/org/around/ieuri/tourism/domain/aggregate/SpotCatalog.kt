package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.LocalDate

/**
 * 장소 하나(SPEC-0019 R-1). 애그리거트 루트이며 조회가 보는 유일한 모델이다.
 * - 언어 무관 값만 갖는다. 이름은 [translations]에만 있고 KO도 그중 하나일 뿐이다.
 */
class SpotCatalog private constructor(
    val id: Long?,
    standard: CatalogStandardValues,
    translations: Map<DomainLocale, CatalogTranslation>,
    festivalPeriod: CatalogFestivalPeriod?,
) {
    /** 원본이 존재하는 언어의 표시값. 없는 언어는 그 언어에 원본이 없다는 뜻이다(SPEC-0029 R-6). */
    val translations: Map<DomainLocale, CatalogTranslation> = translations.toMap()

    /** 언어 무관 표준값. 조회가 카드에 쓰는 값이다. */
    var standard: CatalogStandardValues = standard
        private set

    /** 축제 기간. 축제가 아니면 null이다. */
    var festivalPeriod: CatalogFestivalPeriod? = festivalPeriod
        private set

    /** 표준값을 갱신한다. 배치가 원본을 다시 훑어 확정할 때 쓴다. */
    fun updateStandard(values: CatalogStandardValues) {
        standard = values
    }

    /** 축제 기간을 확정한다. null이면 축제가 아니게 된다(종료·정리로 원본이 사라진 경우). */
    fun updateFestivalPeriod(period: CatalogFestivalPeriod?) {
        festivalPeriod = period
    }

    companion object {
        /** spot 원본 한 행에서 카탈로그를 만든다. 어느 언어로도 만들 수 있다. */
        fun from(
            language: DomainLocale,
            title: String,
            address: String? = null,
            standard: CatalogStandardValues,
            id: Long? = null,
        ): SpotCatalog {
            require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
            return SpotCatalog(
                id = id,
                standard = standard,
                translations = mapOf(language to CatalogTranslation(title, address)),
                festivalPeriod = null,
            )
        }

        /** 저장된 상태를 복원한다. 불변식은 쓰기 시점에 이미 지켜졌다. */
        fun restore(
            id: Long,
            standard: CatalogStandardValues,
            translations: Map<DomainLocale, CatalogTranslation>,
            festivalPeriod: CatalogFestivalPeriod? = null,
        ): SpotCatalog = SpotCatalog(id, standard, translations, festivalPeriod)
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

/** 축제 기간. 언어 무관한 장소 자체의 속성이다. */
data class CatalogFestivalPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!endDate.isBefore(startDate)) { "축제 종료일은 시작일보다 빠를 수 없습니다." }
    }
}
