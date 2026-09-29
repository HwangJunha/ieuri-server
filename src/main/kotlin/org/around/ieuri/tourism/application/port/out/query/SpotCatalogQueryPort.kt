package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 카탈로그 배정·번역이 쓰는 읽기 포트(SPEC-0019).
 */
interface SpotCatalogQueryPort {

    /**
     * 배정 대상 spot — [language]의 `catalog_id`가 null이고 좌표가 유효한 행.
     * - 좌표가 (0,0)이거나 한국 밖인 행은 빠진다.
     * - [afterSpotId] 이후만 읽는다(키셋) — 건너뛴 행이 계속 null이라 커서 없이는 전진하지 못한다.
     */
    fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot>

    /**
     * 매칭 맵. KO 카탈로그의 정규화 제목 키 → 카탈로그 후보 목록.
     * - 같은 키에 카탈로그가 여럿(동명이인)이면 호출자가 좌표로 가른다.
     */
    fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>>

    /**
     * 썸네일 URL → KO 카탈로그 id. 제목 키로 못 찾은 비한국어 spot의 폴백이다.
     * - 그 URL을 쓰는 KO spot이 정확히 하나일 때만 담는다(공유 URL 제외).
     */
    fun loadKoreanThumbnailIndex(): Map<String, Long>
}

/**
 * 배정 대상 spot 한 건. 판정에 필요한 값만 담는다.
 * - 정규화 제목 키는 담지 않는다 — 판정 시점에 도메인([org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer])이 계산한다.
 */
data class UnassignedSpot(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    /** 그 언어의 주소. 카탈로그 번역 행에 함께 심는다(R-4). */
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
)

/** 매칭 맵의 값 하나 — 같은 제목 키를 가진 카탈로그 후보. 좌표·썸네일로 동명이인과 같은 건물의 다른 매장을 가른다. */
data class CatalogCandidate(
    val catalogId: Long,
    val mapX: Double,
    val mapY: Double,
    val thumbnailImageUrl: String,
)
