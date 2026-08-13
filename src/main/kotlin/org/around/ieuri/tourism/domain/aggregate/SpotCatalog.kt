package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource

/**
 * 장소 하나(SPEC-0019 R-1). 언어에 독립적이며, 언어별 spot 행이 이 아래 최대 1개씩 매달린다.
 *
 * 애그리거트 루트다. 번역([translations])은 카탈로그의 일부이며 카탈로그 없이 존재하지 않는다.
 */
class SpotCatalog private constructor(
    val id: Long?,
    val representativeNameKo: String,
    val nameSource: CatalogNameSource,
    translations: Map<DomainLocale, CatalogTranslation>,
) {
    private val _translations: MutableMap<DomainLocale, CatalogTranslation> = translations.toMutableMap()

    val translations: Map<DomainLocale, CatalogTranslation> get() = _translations.toMap()

    /**
     * 이 언어의 제목이 비어 있는가 — 즉 번역 대상인가(R-4).
     *
     * 출처를 보지 않는다. `OFFICIAL`이든 `MACHINE`이든 값이 있으면 채우지 않는다.
     */
    fun needsTranslation(language: DomainLocale): Boolean = language !in _translations

    /**
     * 기계 번역을 채운다. **이미 값이 있으면 아무것도 하지 않는다** — TourAPI가 제공한
     * 제목이 공식 표기이며 우리 번역보다 우선한다(R-4).
     */
    fun fillMachineTranslation(language: DomainLocale, title: String) {
        if (!needsTranslation(language)) return
        _translations[language] = CatalogTranslation(title, TranslationSource.MACHINE)
    }

    /**
     * TourAPI가 발급한 공식 제목을 반영한다. **기존 `MACHINE` 값을 덮어쓴다**(R-5).
     *
     * 이는 R-7의 "재번역 없음"과 충돌하지 않는다 — 새 번역을 만드는 것이 아니라
     * 원본이 도착해 파생값을 밀어내는 것이다.
     */
    fun applyOfficialTitle(language: DomainLocale, title: String) {
        _translations[language] = CatalogTranslation(title, TranslationSource.OFFICIAL)
    }

    companion object {
        /**
         * KO spot 행에서 카탈로그를 만든다. 대표명은 그 행의 제목이며 출처는 `OFFICIAL`이다.
         *
         * KO 제목 자체가 그 언어의 공식 제목이므로 KO 번역 행도 함께 선다 — 조회가
         * `spot_catalog_translation` 하나만 보면 되게 하기 위해서다(설계 문서 §2.2).
         */
        fun fromKoreanSpot(koreanTitle: String, id: Long? = null): SpotCatalog {
            require(koreanTitle.isNotBlank()) { "대표 한국어명은 비어 있을 수 없습니다." }
            return SpotCatalog(
                id = id,
                representativeNameKo = koreanTitle,
                nameSource = CatalogNameSource.OFFICIAL,
                translations = mapOf(DomainLocale.KO to CatalogTranslation(koreanTitle, TranslationSource.OFFICIAL)),
            )
        }

        /**
         * KO 행이 없는 카탈로그를 외국어 제목의 괄호에서 파생시킨다(R-3).
         *
         * **채택 순서는 EN → JA → ZH다.** 관광공사 영문이 로마자 표기의 기준 원본이라 EN이
         * 우선한다. EN 행이 없는 카탈로그가 실재하므로(ZH만 702개, JA만 278개) 존재하는
         * 언어 중 이 순서로 첫 번째를 채택한다.
         *
         * 언어별 괄호값이 서로 다른 경우가 163개 그룹 있다(`초유향`/`초류향`) — 그래서
         * 순서가 규칙이어야 하고, 임의로 고르면 재실행 시 대표명이 바뀐다.
         *
         * 어느 언어에서도 한국어를 뽑지 못하면 null이다 — 호출자가 미해결로 처리한다.
         */
        fun deriveFromForeignTitles(titlesByLanguage: Map<DomainLocale, String>, id: Long? = null): SpotCatalog? {
            val derivedName = DERIVATION_ORDER
                .firstNotNullOfOrNull { language ->
                    titlesByLanguage[language]?.let { SpotTitleNormalizer.extractKoreanKeys(it).firstOrNull() }
                }
                ?: return null

            // 원본 외국어 제목은 그 언어의 공식 표기다 — 파생 카탈로그라도 번역할 필요가 없다.
            val translations = titlesByLanguage.mapValues { (_, title) ->
                CatalogTranslation(title, TranslationSource.OFFICIAL)
            }

            return SpotCatalog(
                id = id,
                representativeNameKo = derivedName,
                nameSource = CatalogNameSource.DERIVED,
                translations = translations,
            )
        }

        /** 저장된 상태를 복원한다. 불변식은 쓰기 시점에 이미 지켜졌다. */
        fun restore(
            id: Long,
            representativeNameKo: String,
            nameSource: CatalogNameSource,
            translations: Map<DomainLocale, CatalogTranslation>,
        ): SpotCatalog = SpotCatalog(id, representativeNameKo, nameSource, translations)

        /** R-3의 채택 순서. 목록의 순서가 곧 우선순위다. */
        private val DERIVATION_ORDER = listOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}

/** 카탈로그의 한 언어 제목과 그 출처. */
data class CatalogTranslation(
    val title: String,
    val source: TranslationSource,
) {
    init {
        require(title.isNotBlank()) { "번역 제목은 비어 있을 수 없습니다." }
    }
}
