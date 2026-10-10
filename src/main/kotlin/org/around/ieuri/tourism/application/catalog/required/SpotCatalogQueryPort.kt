package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.domain.catalog.CatalogCandidate
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 카탈로그 정비가 읽는 것(SPEC-0019·SPEC-0029). 원본 spot과 KO 카탈로그 색인이다. */
interface SpotCatalogQueryPort {

    /**
     * 배정 대상 원본 — [language]의 `catalog_id`가 null이고 좌표가 한국 안인 행, spot id 오름차순.
     * - [afterSpotId] 이후만 읽는다(키셋).
     */
    fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<CatalogOrigin>

    /** KO 번역 제목의 매칭 키 → 카탈로그 후보. 같은 키에 후보가 여럿(동명이인)이면 도메인이 좌표로 가른다. */
    fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>>

    /** 썸네일 URL → KO 카탈로그 id. 그 URL을 쓰는 KO 원본이 정확히 하나일 때만 담는다(공유 URL 제외). */
    fun loadKoreanThumbnailIndex(): Map<String, Long>

    /** 원본이 [minOriginCount]개를 **초과**하고 원본 간 최대 거리가 [minDistanceMeters] **이상**인 카탈로그 id. */
    fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long>

    /** 카탈로그에 붙은 원본 전부(좌표가 한국 안인 것). 언어 순서(KO→EN→JA→ZH), 같은 언어면 id 순이다. */
    fun findOrigins(catalogId: Long): List<CatalogOrigin>
}
