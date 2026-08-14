package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.TranslationSource
import java.time.LocalDate

/**
 * 장소 하나(SPEC-0019 R-1). 애그리거트 루트이며 **조회가 보는 유일한 모델이다.**
 *
 * `spot`은 원본(offer)으로 남고 카탈로그는 그것을 묶은 표준 상품에 해당한다 — 상품 카탈로그의
 * 구조와 같다. 그래서 카탈로그는 원본을 흡수하지 않고 **표준값만 확정해 갖는다.**
 *
 * 표준값은 전부 **KO 우선**으로 정한다. KO가 원본 언어이고 가장 많으며(56,425행) 번역 기준이다.
 */
class SpotCatalog private constructor(
    val id: Long?,
    val representativeNameKo: String,
    val nameSource: CatalogNameSource,
    standard: CatalogStandardValues,
    translations: Map<DomainLocale, CatalogTranslation>,
    festivalPeriod: CatalogFestivalPeriod?,
) {
    private val _translations: MutableMap<DomainLocale, CatalogTranslation> = translations.toMutableMap()

    val translations: Map<DomainLocale, CatalogTranslation> get() = _translations.toMap()

    /** 언어 무관 표준값. 조회가 카드에 쓰는 값이다. */
    var standard: CatalogStandardValues = standard
        private set

    /** 축제 기간. 축제가 아니면 null이다. */
    var festivalPeriod: CatalogFestivalPeriod? = festivalPeriod
        private set

    /**
     * 매칭 조회 키. 비한국어 행이 이 값으로 카탈로그를 찾는다.
     *
     * 대표명에서 파생되므로 컬럼으로 들고 다니지 않고 필요할 때 계산한다 — 둘이 어긋날 여지를
     * 없앤다. 저장은 어댑터가 이 값을 컬럼에 넣는다(인덱스 때문).
     */
    val nameKey: String? get() = SpotTitleNormalizer.matchKey(representativeNameKo)

    /** 이 언어의 표시값이 비어 있는가 — 즉 번역 대상인가(R-4). 출처는 보지 않는다. */
    fun needsTranslation(language: DomainLocale): Boolean = language !in _translations

    /**
     * 기계 번역을 채운다. **이미 값이 있으면 아무것도 하지 않는다** — TourAPI가 제공한 값이
     * 공식 표기이며 우리 번역보다 우선한다(R-4).
     */
    fun fillMachineTranslation(language: DomainLocale, title: String, address: String? = null) {
        if (!needsTranslation(language)) return
        _translations[language] = CatalogTranslation(title, address, TranslationSource.MACHINE)
    }

    /**
     * TourAPI가 발급한 공식 값을 반영한다. **기존 `MACHINE`을 덮어쓴다**(R-5).
     *
     * R-7의 "재번역 없음"과 충돌하지 않는다 — 새 번역을 만드는 것이 아니라 원본이 도착해
     * 파생값을 밀어내는 것이다.
     */
    fun applyOfficial(language: DomainLocale, title: String, address: String? = null) {
        _translations[language] = CatalogTranslation(title, address, TranslationSource.OFFICIAL)
    }

    /** 표준값을 갱신한다. 배치가 원본을 다시 훑어 확정할 때 쓴다. */
    fun updateStandard(values: CatalogStandardValues) {
        standard = values
    }

    /** 축제 기간을 확정한다. null이면 축제가 아니게 된다(종료·정리로 원본이 사라진 경우). */
    fun updateFestivalPeriod(period: CatalogFestivalPeriod?) {
        festivalPeriod = period
    }

    companion object {
        /** KO spot 행에서 카탈로그를 만든다. KO 제목 자체가 그 언어의 공식 값이므로 KO 번역도 함께 선다. */
        fun fromKoreanSpot(
            koreanTitle: String,
            koreanAddress: String? = null,
            standard: CatalogStandardValues = CatalogStandardValues(),
            id: Long? = null,
        ): SpotCatalog {
            require(koreanTitle.isNotBlank()) { "대표 한국어명은 비어 있을 수 없습니다." }
            return SpotCatalog(
                id = id,
                representativeNameKo = koreanTitle,
                nameSource = CatalogNameSource.OFFICIAL,
                standard = standard,
                translations = mapOf(
                    DomainLocale.KO to CatalogTranslation(koreanTitle, koreanAddress, TranslationSource.OFFICIAL),
                ),
                festivalPeriod = null,
            )
        }

        /**
         * KO 행이 없는 카탈로그를 외국어 제목의 괄호에서 파생시킨다(R-3).
         *
         * **채택 순서는 EN → JA → ZH다.** 관광공사 영문이 로마자 표기의 기준 원본이라 EN이
         * 우선한다. EN 행이 없는 카탈로그가 실재하므로(ZH만 702개, JA만 278개) 존재하는 언어
         * 중 이 순서로 첫 번째를 채택한다. 언어별 괄호값이 다른 경우가 163개 그룹 있어
         * (`초유향`/`초류향`) 순서가 규칙이어야 하며, 임의로 고르면 재실행 시 대표명이 바뀐다.
         *
         * 어느 언어에서도 한국어를 뽑지 못하면 null이다 — 호출자가 미해결로 처리한다.
         */
        fun deriveFromForeign(
            cards: Map<DomainLocale, CatalogTranslation>,
            standard: CatalogStandardValues = CatalogStandardValues(),
            id: Long? = null,
        ): SpotCatalog? {
            val derivedName = DERIVATION_ORDER
                .firstNotNullOfOrNull { language ->
                    cards[language]?.let { SpotTitleNormalizer.extractKoreanKeys(it.title).firstOrNull() }
                }
                ?: return null

            return SpotCatalog(
                id = id,
                representativeNameKo = derivedName,
                nameSource = CatalogNameSource.DERIVED,
                standard = standard,
                // 원본 외국어 값은 그 언어의 공식 표기다 — 파생 카탈로그라도 번역할 필요가 없다.
                translations = cards,
                festivalPeriod = null,
            )
        }

        /** 저장된 상태를 복원한다. 불변식은 쓰기 시점에 이미 지켜졌다. */
        fun restore(
            id: Long,
            representativeNameKo: String,
            nameSource: CatalogNameSource,
            standard: CatalogStandardValues,
            translations: Map<DomainLocale, CatalogTranslation>,
            festivalPeriod: CatalogFestivalPeriod? = null,
        ): SpotCatalog = SpotCatalog(id, representativeNameKo, nameSource, standard, translations, festivalPeriod)

        /** R-3의 채택 순서. 목록의 순서가 곧 우선순위다. */
        private val DERIVATION_ORDER = listOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}

/**
 * 카탈로그의 언어 무관 표준값 — 조회가 카드에 쓰는 값이다.
 *
 * 전부 nullable인 이유는 **원본에 결손이 있기 때문이다** — 좌표는 (0,0)·한국 밖이 317건이고,
 * 그런 카탈로그는 좌표 없이 존재한다(지도·반경 조회에서 빠진다).
 */
data class CatalogStandardValues(
    val mapX: Double? = null,
    val mapY: Double? = null,
    val thumbnailImageUrl: String? = null,
    val category: SpotCategory? = null,
    val copyright: CopyrightType? = null,
) {
    init {
        // 좌표는 쌍으로 있거나 함께 없다. 하나만 있으면 위치를 만들 수 없다.
        require((mapX == null) == (mapY == null)) { "좌표는 함께 있거나 함께 없어야 합니다." }
    }

    /** 지도·반경 조회에 나타날 수 있는가. */
    val hasLocation: Boolean get() = mapX != null && mapY != null
}

/**
 * 카탈로그의 한 언어 표시값 — 제목과 주소, 그리고 출처.
 *
 * **주소가 함께 있어야 한다**(R-4). 제목만 있으면 사용자가 목록에서 이름은 읽어도 찾아갈 수
 * 없다. 원문에 주소가 없는 spot이 있어 [address]는 nullable이다.
 */
data class CatalogTranslation(
    val title: String,
    val address: String? = null,
    val source: TranslationSource,
) {
    init {
        require(title.isNotBlank()) { "번역 제목은 비어 있을 수 없습니다." }
    }
}

/** 축제 기간. 원본은 `spot_festival`에 있고 이것은 KO 우선으로 확정한 표준값이다. */
data class CatalogFestivalPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!endDate.isBefore(startDate)) { "종료일은 시작일보다 앞설 수 없습니다." }
    }

    /** [asOf] 기준 진행 중인가. 종료일이 오늘이면 아직 진행 중이다. */
    fun isOngoing(asOf: LocalDate): Boolean = !endDate.isBefore(asOf)
}
