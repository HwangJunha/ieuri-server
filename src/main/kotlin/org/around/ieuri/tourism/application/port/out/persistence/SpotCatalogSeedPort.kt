package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 초기 번역 시드 적재 포트(SPEC-0019 §5.4). 일회성이며 배치가 호출한다.
 */
interface SpotCatalogSeedPort {

    /** 시드 한 묶음을 적재하고 결과를 돌려준다. */
    fun loadSeed(entries: List<SeededTranslation>): SeedLoadResult
}

/**
 * 시드 한 항목.
 *
 * [koSpotIds]로 KO 행을 찾고 그 행의 `catalog_id`에 [titles]를 붙인다.
 * [expectedKoreanTitle]·[expectedMapX]·[expectedMapY]는 검증용이며, 찾은 행과 다르면
 * 그 사이 TourAPI가 데이터를 바꾼 것이므로 적재하지 않는다.
 */
data class SeededTranslation(
    val koSpotIds: List<Long>,
    val expectedKoreanTitle: String,
    val expectedMapX: Double,
    val expectedMapY: Double,
    /**
     * 언어별 카드(제목·주소). **주소가 함께 온다**(R-4) — 제목만 있으면 사용자가 목록에서
     * 이름은 읽어도 찾아갈 수 없다. 시드에 주소가 없는 항목은 address가 null이다.
     */
    val cards: Map<DomainLocale, SeededCard>,
)

/** 시드의 언어별 카드 한 건. */
data class SeededCard(val title: String, val address: String?)

/** [mismatched]는 시드와 DB가 어긋나 건너뛴 항목 수다. 크면 시드가 낡은 것이다. */
data class SeedLoadResult(
    val loaded: Int,
    val mismatched: Int,
)
