package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 카탈로그 배정·번역·보고가 쓰는 읽기 포트(SPEC-0019).
 */
interface SpotCatalogQueryPort {

    /**
     * 배정 대상 spot — `catalog_id`가 null인 행. **KO를 먼저 돌려준다** — KO 행이 카탈로그를
     * 만들고 나머지 언어가 거기 붙으므로, 순서가 뒤집히면 KO가 있는 장소인데도 파생
     * 카탈로그가 생긴다.
     *
     * **이것이 곧 증분 처리다**(설계 문서 §3.3) — 배정된 행은 다시 판정하지 않는다.
     * 적재가 새 행을 넣거나 정리가 참조를 끊으면 그 행만 자연히 대상이 된다.
     */
    fun findUnassigned(limit: Int): List<UnassignedSpot>

    /**
     * 같은 장소로 묶인 비한국어 spot들의 제목. 파생 카탈로그의 대표명을 EN→JA→ZH 순으로
     * 고르기 위한 입력이다(R-3).
     */
    fun findForeignCardsByThumbnail(thumbnailImageUrl: String): Map<DomainLocale, ForeignCard>

    /** 번역이 비어 있는 (카탈로그, 언어) 쌍. */
    fun findCatalogsMissingTranslation(languages: Set<DomainLocale>, limit: Int): List<UntranslatedCatalog>

    /** 미해결 건 전량. CSV 보고의 입력이다(R-8). */
    fun findUnresolved(): List<UnresolvedSpotReport>
}

/** 같은 장소로 묶인 비한국어 행의 언어별 카드. 파생 카탈로그가 이 값으로 선다. */
data class ForeignCard(val title: String, val address: String?)

/** 배정 대상 spot 한 건. 판정에 필요한 값만 담는다. */
data class UnassignedSpot(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    /** 그 언어의 주소. 카탈로그 번역 행에 함께 심는다(R-4). */
    val address: String?,
    val titleKey: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
)

/** 번역이 필요한 카탈로그. [representativeNameKo]가 번역 원문이다. */
data class UntranslatedCatalog(
    val catalogId: Long,
    val representativeNameKo: String,
    /** 번역 원문이 되는 KO 주소. 원문에 주소가 없으면 null이다(R-4). */
    val representativeAddressKo: String?,
    val missingLanguages: Set<DomainLocale>,
)

/**
 * 미해결 건 한 줄. CSV에 그대로 실린다.
 *
 * [candidates]는 사람이 판정하려면 "무엇과 헷갈렸는가"를 알아야 하기 때문에 후보별 좌표·
 * 카테고리까지 담는다.
 */
data class UnresolvedSpotReport(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    val category: SpotCategory,
    val mapX: Double,
    val mapY: Double,
    val reason: String,
    val extractedKey: String?,
    val thumbnailImageUrl: String,
    val candidates: List<UnresolvedCandidate>,
)

/** 미해결 건의 KO 후보 하나. */
data class UnresolvedCandidate(
    val spotId: Long,
    val title: String,
    val category: SpotCategory,
    val mapX: Double,
    val mapY: Double,
)
