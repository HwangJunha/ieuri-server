package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.application.query.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.query.PopularSpotView
import org.around.ieuri.tourism.application.query.PopularSpotsQuery
import org.around.ieuri.tourism.application.query.SearchSpotsQuery
import org.around.ieuri.tourism.application.query.SpotSearchResultView
import org.around.ieuri.tourism.application.query.SpotSuggestionView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * spot 발견 조회 인바운드 포트(SPEC-0013). 검색·자동완성·진행 중 축제·인기 목록 네 가지로,
 * "사용자가 아직 모르는 spot을 찾는" 경로를 모은다. 상세·좋아요 목록처럼 이미 아는 spot을
 * 다루는 [SpotQueryUseCase]와 분리한 이유다.
 *
 * 언어는 요청 스코프에서 결정돼 명시 파라미터로 전달된다 — application은 서블릿/헤더를 모른다.
 * 네 조회 모두 좌표를 필수로 받는다(위치 권한이 전제다).
 */
interface SpotDiscoveryUseCase {
    /**
     * 반경 내 spot 전문 검색(R1-R8). 관련도(거리 가중) 내림차순, 동점은 id 오름차순이다.
     *
     * **오프셋 페이지네이션이다** — 관련도가 검색 엔진의 계산값이라 커서 조건으로 표현할 수
     * 없다. 페이지 상한(PAGE_TOO_DEEP)도 userErrors로 나간다.
     *
     * **입력 검증 실패를 예외가 아니라 값으로 돌려준다** — 빈 질의·길이 초과는
     * [SpotSearchResultView.userErrors]에 담기고 페이지는 비어 있다(POL-015). 좌표·반경 오류는
     * 다른 조회와 같은 규약이라 예외로 전파된다.
     *
     * 검증에 실패하면 검색 포트를 호출하지 않는다(AC-8.1).
     */
    fun searchSpots(query: SearchSpotsQuery, locale: DomainLocale): SpotSearchResultView

    /**
     * 제목 접두 자동완성(R9). 빈 질의는 오류가 아니라 빈 목록이며, 이때도 포트를 호출하지 않는다.
     * 페이지네이션하지 않는다 — 상한까지 한 번에 준다.
     */
    fun spotSearchSuggestions(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
        locale: DomainLocale,
    ): List<SpotSuggestionView>

    /**
     * 반경 내 진행 중 축제(R17). '오늘'은 서버 시계에서 오며 경계를 포함한다 — 오늘이 시작일
     * 또는 종료일과 같아도 진행 중이다. 거리 오름차순, 동점은 id 오름차순.
     */
    fun ongoingFestivals(query: OngoingFestivalsQuery, locale: DomainLocale): CursorPage<FestivalView>

    /**
     * 반경 내 인기 spot(R18). 누적 좋아요 내림차순, 동점은 id 오름차순. 기간 제한이 없다.
     * 좋아요가 정책 하한 미만인 spot은 제외되며, 결과가 0건인 것은 정상이다.
     */
    fun popularSpots(query: PopularSpotsQuery, locale: DomainLocale): CursorPage<PopularSpotView>
}
