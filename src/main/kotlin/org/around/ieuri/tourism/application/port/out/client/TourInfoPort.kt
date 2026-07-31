package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광 정보 조회 아웃바운드 포트(도메인 지향). 도메인 모델([Spot])을 반환하고, TourAPI 필드명·코드 체계는
 * 어댑터 뒤에 격리한다. **향후 DB 적재로 전환 = 이 포트의 구현체 교체만.** 도메인·application·GraphQL 무변경.
 *
 * 어댑터가 이미 끝낸 것: TourAPI 호출·역직렬화·함정 5종 처리, 비관광 필터, 이미지 https 정규화,
 * 언어→enum 코드 매핑, 좌표 파싱. 어댑터가 하지 않는 것: 거리 계산·정렬·커서(= application 책임).
 */
interface TourInfoPort {
    /**
     * 주변 관광지 한 페이지(upstream 페이지)를 도메인 모델로 반환한다. 거리·정렬·커서는 application이 담당하므로
     * 여기선 upstream이 준 순서 그대로(이미지 보유·비관광 필터만 적용) 돌려준다.
     * upstream 장애·XML 오류 등은 UpstreamUnavailableException으로 변환해 던진다.
     */
    fun findNearby(query: NearbySpotQuery): NearbySpotResult
}

/**
 * 주변 조회 입력. [category]가 null이면 전체(카테고리 미지정) 조회.
 * [pageNo]/[numOfRows]는 upstream 페이지네이션 파라미터(1-base).
 */
data class NearbySpotQuery(
    val locale: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val pageNo: Int,
    val numOfRows: Int,
)

/**
 * 주변 조회 결과. [spots]는 이 upstream 페이지에서 필터를 통과한 도메인 모델(거리는 application이 채운다),
 * [totalCount]는 TourAPI가 준 필터-이전 총계(참고용), [hasMoreUpstreamPages]는 다음 upstream 페이지 존재 여부.
 */
data class NearbySpotResult(
    val spots: List<Spot>,
    val totalCount: Int?,
    val hasMoreUpstreamPages: Boolean,
)
