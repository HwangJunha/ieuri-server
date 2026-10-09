package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.catalog.policy.OriginProximityClustering
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/**
 * 장소 하나(SPEC-0019 R-1). 언어별 TourAPI 원본(spot)이 여기에 붙고, 원본이 있는 언어마다 번역이 하나씩 생긴다(SPEC-0029 R-6).
 * - 번역은 언어 수(최대 4)로 묶여 있어 루트가 들고 있다. 반응은 수가 정해져 있지 않아 [react]가 만들어 내보낸다.
 * - 표준값은 처음 붙은 원본에서 오며 이후 바뀌지 않는다.
 */
data class SpotCatalog(
    val id: Long?,
    val standard: CatalogStandardValues,
    val translations: List<CatalogTranslation>,
) {
    init {
        require(translations.isNotEmpty()) { "카탈로그에는 번역이 하나 이상 있어야 합니다." }
        require(translations.distinctBy { it.language }.size == translations.size) { "언어마다 번역은 하나입니다." }
    }

    fun translationIn(language: DomainLocale): CatalogTranslation? = translations.firstOrNull { it.language == language }

    /**
     * 원본 하나를 붙였을 때 바뀌는 번역. 바뀌는 것이 없으면 null이다.
     * - 그 언어 번역이 이미 있으면 제목·주소를 덮지 않는다(SPEC-0019 R-4). 감춰져 있었다면 되살린다 — 원본이 돌아왔다는 뜻이다.
     */
    fun attach(origin: CatalogOrigin): CatalogTranslation? {
        val existing = translationIn(origin.language) ?: return origin.toTranslation()
        return if (existing.visible) null else existing.copy(visible = true)
    }

    /** 좋아요/싫어요. 이 언어에 보이는 번역이 없으면 그 언어에서는 볼 수 없는 장소다(SPEC-0029 R-12). */
    fun react(memberId: Long, language: DomainLocale, reaction: ReactionType): SpotReaction {
        if (translationIn(language)?.visible != true) throw SpotNotFoundInLocaleException()
        return SpotReaction(memberId = memberId, catalogId = requireNotNull(id) { "적재된 카탈로그에만 반응한다." }, language = language, reaction = reaction)
    }

    /**
     * 서로 다른 장소가 접혔으면 가른다(SPEC-0029). 원본이 한 군집이면 null이다.
     * - 가장 큰 군집이 이 카탈로그에 남는다(동수면 앞선 군집). 나머지 군집은 각자 새 카탈로그가 된다.
     * - 남는 군집에 없는 언어의 번역은 이 카탈로그에서 지운다.
     */
    fun split(origins: List<CatalogOrigin>): CatalogSplit? {
        val clusters = OriginProximityClustering.cluster(origins) { it.mapX to it.mapY }
        if (clusters.size < 2) return null

        val stays = clusters.maxBy { it.size }
        val leaving = clusters.filter { it !== stays }
        val stayingLanguages = stays.map { it.language }.toSet()
        return CatalogSplit(
            departures = leaving.map { cluster -> CatalogSplit.Departure(foundFrom(cluster), cluster.map { it.spotId }) },
            removedLanguages = leaving.flatten().map { it.language }.distinct().filterNot { it in stayingLanguages },
        )
    }

    companion object {
        /** 대표 원본과 축제 기간을 고르는 언어 순서. */
        val LANGUAGE_PRIORITY = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)

        /** 원본 하나로 새 카탈로그를 만든다. 어느 언어로도 만들 수 있다. */
        fun found(origin: CatalogOrigin): SpotCatalog = foundFrom(listOf(origin))

        /**
         * 원본 묶음으로 새 카탈로그를 만든다. 표준값은 [LANGUAGE_PRIORITY]의 첫 원본에서 온다.
         * - 같은 언어 원본이 둘이면 먼저 온 것의 제목을 쓴다.
         */
        fun foundFrom(origins: List<CatalogOrigin>): SpotCatalog {
            val representative = origins.minBy { LANGUAGE_PRIORITY.indexOf(it.language) }
            val ordered = listOf(representative) + origins.filter { it !== representative }
            return SpotCatalog(
                id = null,
                standard = representative.standardValues(),
                translations = ordered.distinctBy { it.language }.map { it.toTranslation() },
            )
        }

        /** 원본 축제 기간 중 카탈로그가 쓸 하나. 언어 순서([LANGUAGE_PRIORITY]), 같은 언어면 원본 id 순이다. 없으면 null. */
        fun festivalPeriodOf(originPeriods: List<OriginFestivalPeriod>): FestivalPeriod? = originPeriods
            .minWithOrNull(compareBy<OriginFestivalPeriod> { LANGUAGE_PRIORITY.indexOf(it.language) }.thenBy { it.spotId })
            ?.period
    }
}
