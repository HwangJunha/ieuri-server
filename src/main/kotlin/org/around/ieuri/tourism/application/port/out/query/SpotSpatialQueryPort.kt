package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 반경 조회 아웃바운드 포트. **생 SQL 어댑터가 이 포트 뒤에 있다** — PostGIS 함수
 * (`ST_DWithin`·`ST_Distance`·`ST_MakePoint`)를 QueryDSL로 표현할 수 없기 때문이다.
 *
 * [SpotQueryPort]와 갈라 두는 이유는 `infrastructure → infrastructure` 주입을 막기 위해서다.
 * 어댑터가 다른 어댑터를 주입받으면 "어느 경로가 생 SQL을 쓰는가"가 애플리케이션에서 보이지 않는다.
 *
 * 조회와 카운트가 **같은 조건**을 써야 하므로 둘이 한 포트에 있다 — 갈리면 hasNext와 totalCount가
 * 어긋난다.
 */
interface SpotSpatialQueryPort {
    /**
     * 반경 안의 관광지를 **거리 오름차순**으로 한 페이지 조회한다. TourAPI를 호출하지 않고 적재된
     * 카탈로그·번역만 본다. [SpotSearchResult.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /**
     * 랭킹에 쓸 후보만. **썸네일·저작권을 채우지 않는다** — 그 둘만 카탈로그에 있어
     * 후보 전부에 조인하면 버려질 행까지 읽는다(SPEC-0038).
     *
     * 추천은 후보를 넉넉히 뽑아 점수·재배열을 거친 뒤 앞 일부만 쓰므로, 살아남은 id로
     * [findCards]를 호출해 나머지를 채운다. 후보를 그대로 다 쓰는 [searchNearby]와 갈라
     * 두는 이유다 — 그쪽은 뽑은 만큼 응답하므로 한 번에 조인하는 편이 싸다.
     */
    fun searchNearbyCandidates(query: NearbySpotSearch): List<SpotCandidate>

    /**
     * [spotIds]의 카드 정보. 카탈로그에만 있는 썸네일·저작권을 PK로 읽는다.
     *
     * **순서를 보장하지 않는다** — 호출자가 이미 정한 순서대로 다시 세운다(POL-019).
     * 없는 id는 결과에서 빠진다(그 사이 삭제된 경우).
     */
    fun findCards(spotIds: Collection<Long>): Map<Long, SpotCard>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int
}

/**
 * 랭킹 단계의 후보 한 건. 점수 계산과 카테고리 재배열에 필요한 것만 담는다 —
 * 카드 표시용 썸네일·저작권은 [SpotCard]가 뒤에서 채운다.
 */
data class SpotCandidate(
    val spotId: Long,
    val title: String,
    val address: String?,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val distanceMeters: Double,
)

/** 카탈로그에만 있는 카드 필드. [SpotCandidate]와 합쳐야 응답이 된다. */
data class SpotCard(
    val thumbnailImageUrl: String,
    val copyright: CopyrightType,
)
