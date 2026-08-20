package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 카탈로그 배정·번역이 쓰는 읽기 포트(SPEC-0019).
 */
interface SpotCatalogQueryPort {

    /**
     * 배정 대상 spot — `catalog_id`가 null이고 **좌표가 유효한** 행.
     *
     * 좌표가 (0,0)이거나 한국 밖인 행은 여기서 빠진다. 위치를 모르는 장소가 아니라 깨진
     * 행이며(실측 317건), 카탈로그를 내주면 지도·반경 조회에서 빠지는 "존재하지만 아무 데도
     * 안 나오는" 데이터가 된다.
     *
     * [language]로 좁혀 읽는다 — 배치가 KO를 먼저 전량 처리해 카탈로그를 만든 뒤, 나머지
     * 언어를 그 위에 붙이기 때문이다.
     *
     * **[afterSpotId] 이후만 읽는다(키셋).** 배정하지 못하고 건너뛴 행은 `catalog_id`가 그대로
     * null이라, 커서가 없으면 다음 회차가 같은 행을 다시 읽어 앞으로 나아가지 못한다.
     */
    fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot>

    /**
     * 매칭 맵. **KO 카탈로그의 정규화 제목 키 → 카탈로그 id.**
     *
     * 비한국어 spot이 제목 괄호에서 뽑은 한국어로 이 맵을 조회한다. 배치가 시작할 때 한 번
     * 읽어 메모리에 들고 있으므로 **spot 한 행마다 DB를 왕복하지 않는다** — 그래서 이 키에
     * 인덱스도, 저장 컬럼도 필요 없다.
     *
     * 같은 키에 카탈로그가 여럿이면(동명이인) 좌표로 가른다. 그래서 값이 후보 목록이다.
     */
    fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>>

    /**
     * 썸네일 URL → KO 카탈로그 id. **그 URL을 쓰는 KO spot이 정확히 하나일 때만 담는다.**
     *
     * 제목 키로 못 찾은 비한국어 spot의 폴백이다. TourAPI가 같은 장소의 언어별 제목을 다르게
     * 주는 경우가 실재하고(`한화리조트 용인 베잔송` vs `한화리조트 / 용인`), 그때도 썸네일은
     * 같다 — 실측 2,738건이 이 경로로 붙는다.
     *
     * **여럿이 공유하는 URL은 제외한다.** 여행 코스가 개별 명소의 대표 사진을 재사용하므로
     * 공유 URL은 같은 장소라는 근거가 되지 못한다. 유일한 경우로 좁히면 카테고리 일치율이
     * 97%다(실측).
     */
    fun loadKoreanThumbnailIndex(): Map<String, Long>
}

/**
 * 배정 대상 spot 한 건. 판정에 필요한 값만 담는다.
 *
 * 정규화된 제목 키를 담지 않는 이유는 **판정 시점에 도메인이 계산하기 때문이다**
 * ([org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer]). 원본 컬럼으로 들고
 * 오면 정규화 규칙이 바뀌었을 때 적재 시점의 낡은 키로 매칭한다.
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

/**
 * 매칭 맵의 값 하나 — 같은 제목 키를 가진 카탈로그 후보.
 *
 * 좌표를 담는 이유는 동명이인을 가르기 위해서다. `롯데마트`처럼 전국에 같은 이름이 여럿이면
 * 제목만으로는 정할 수 없다.
 *
 * **썸네일도 담는다.** 좌표만으로는 *같은 건물 안의 다른 매장*을 가르지 못한다 — 아울렛
 * 입점 매장들은 좌표가 실제로 동일하다(실측: 카탈로그 108개가 이 경로로 잘못 뭉쳤다).
 */
data class CatalogCandidate(
    val catalogId: Long,
    val mapX: Double,
    val mapY: Double,
    val thumbnailImageUrl: String,
)
