package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.LocalDate

/**
 * 장소 하나(SPEC-0019 R-1). 애그리거트 루트이며 **조회가 보는 유일한 모델이다.**
 *
 * `spot`은 원본(offer)으로 남고 카탈로그는 그것을 묶은 표준 상품에 해당한다 — 상품 카탈로그의
 * 구조와 같다. 그래서 카탈로그는 원본을 흡수하지 않고 **언어 무관 값만 확정해 갖는다.**
 *
 * **이름을 갖지 않는다.** 이름은 언어에 종속된 값이므로 [translations]에만 있고, KO도 그중
 * 하나의 언어일 뿐이다. 이 구분이 없으면 "한국어 이름이 없는 장소"(외국어에만 존재하는 spot)를
 * 표현할 수 없어, 결국 원본에 없는 한국어명을 지어내게 된다.
 */
class SpotCatalog private constructor(
    val id: Long?,
    standard: CatalogStandardValues,
    translations: Map<DomainLocale, CatalogTranslation>,
    festivalPeriod: CatalogFestivalPeriod?,
) {
    /**
     * 원본이 존재하는 언어의 표시값. **채워 넣는 연산이 없다** — 여기 없는 언어는 그 언어에
     * 원본이 없다는 뜻이고, 빈 채로 남는 것이 올바른 상태다(SPEC-0029 R-6).
     */
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
        /**
         * spot 원본 한 행에서 카탈로그를 만든다.
         *
         * **어느 언어로도 만들 수 있다.** KO 원본이 있으면 KO로 만들어지고, 외국어에만 있는
         * 장소는 그 언어로 만들어진다 — 후자는 한국어 번역 행이 없는 채로 존재하며, 그것이
         * 올바른 상태다. 없는 한국어명을 만들어 채우지 않는다.
         */
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
 *
 * **전부 필수다.** 좌표가 없거나 (0,0)이거나 한국 밖인 원본은 위치를 모르는 장소가 아니라
 * 깨진 행이며(실측 317건), 배치가 적재 단계에서 제외한다. nullable로 열면 지도·반경 조회에서
 * 빠지는 "존재하지만 아무 데도 안 나오는" 카탈로그가 생긴다.
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
        fun isValidCoordinate(mapX: Double, mapY: Double): Boolean =
            mapX in KOREA_LON_RANGE && mapY in KOREA_LAT_RANGE
    }
}

/**
 * 카탈로그의 한 언어 표시값 — 제목과 주소.
 *
 * **이 행의 의미는 "이 언어에 원본이 존재한다는 증거"다**(SPEC-0029 R-6). 이전에는 "이 언어의
 * 표시값"이었고 비어 있으면 기계번역으로 채웠으나, 그 채움을 걷어냈다. 이제 여기 있는 값은
 * 전부 업스트림 원문이며, 없는 언어는 없는 채로 조회에서 빠진다.
 *
 * 원문에 주소가 없는 spot이 있어 [address]는 nullable이다.
 */
data class CatalogTranslation(
    val title: String,
    val address: String? = null,
) {
    init {
        require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
    }
}

/**
 * 축제 기간. **언어 무관한 장소 자체의 속성이다** — 실측상 다국어 축제 60개 중 기간이 다른
 * 것은 3개뿐이다.
 */
data class CatalogFestivalPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!endDate.isBefore(startDate)) { "축제 종료일은 시작일보다 빠를 수 없습니다." }
    }
}
