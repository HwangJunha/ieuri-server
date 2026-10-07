package org.around.ieuri.tourism.application.catalog.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.provided.dto.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SearchSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSearchResultView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** spot 발견 조회(SPEC-0013) — 검색·자동완성·진행 중 축제·인기 목록. 네 조회 모두 좌표가 필수다. */
interface SpotDiscoveryUseCase {
    /**
     * 반경 내 spot 전문 검색(오프셋 페이지). 관련도(거리 가중) 내림차순, 동점은 id 오름차순이다.
     * - 빈 질의·길이 초과·PAGE_TOO_DEEP은 [SpotSearchResultView.userErrors]로 돌아오고 페이지는 빈다(POL-015).
     * - 좌표·반경 오류는 예외로 전파된다. 검증에 실패하면 검색 포트를 호출하지 않는다.
     */
    fun searchSpots(query: SearchSpotsQuery, locale: DomainLocale): SpotSearchResultView

    /** 제목 접두 자동완성. 빈 질의는 오류가 아니라 빈 목록이며, 상한까지 한 번에 준다. */
    fun spotSearchSuggestions(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
        locale: DomainLocale,
    ): List<SpotSuggestionView>

    /** 반경 내 진행 중 축제. 시작일·종료일 당일도 진행 중이다. 거리 오름차순, 동점은 id 오름차순. */
    fun ongoingFestivals(query: OngoingFestivalsQuery, locale: DomainLocale): CursorPage<FestivalView>

    /** 반경 내 인기 spot. 누적 좋아요 내림차순, 동점은 id 오름차순. 좋아요가 정책 하한 미만이면 제외된다. */
    fun popularSpots(query: PopularSpotsQuery, locale: DomainLocale): CursorPage<PopularSpotView>
}
