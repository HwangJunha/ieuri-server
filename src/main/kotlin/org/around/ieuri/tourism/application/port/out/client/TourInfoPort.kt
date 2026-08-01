package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광 정보 조회 아웃바운드 포트. 어댑터가 TourAPI 세부(필드명·코드 체계)를 모두 해소한 **중립 데이터**
 * ([NearbySpotData])를 반환하고, application이 이를 도메인 모델(Spot)로 변환한다(DTO→Domain).
 * **향후 DB 적재로 전환 = 이 포트의 구현체 교체만.** 도메인·application·GraphQL 무변경.
 *
 * 어댑터가 하는 것: 호출·역직렬화·resultCode 판정, 비관광 필터, 이미지 https 정규화, 코드→도메인 enum 매핑.
 * 어댑터가 하지 않는 것: 거리 계산·정렬·커서·도메인 조립(= application 책임).
 */
interface TourInfoPort {
    /**
     * 주변 관광지 한 upstream 페이지를 중립 데이터로 반환한다. upstream 순서 그대로(필터만 적용).
     * upstream 장애는 UpstreamUnavailableException으로 변환해 던진다.
     */
    fun findNearby(query: NearbySpotQuery): NearbySpotResult

    /**
     * contentId 단건 상세를 도메인 [SpotDetail]로 반환한다(SPEC-0003). 어댑터가 detailCommon2·detailImage2 2콜을
     * 병합하고 이미지 https 정규화·10장 컷·빈문자열 null화·저작권 조립까지 끝낸 상태로 넘긴다. [SpotDetail.likeCount]는
     * 여기서 채우지 않는다(캐시 경계 분리 — application이 실시간 집계로 얹음).
     *
     * upstream이 해소하지 못하면 SpotNotFoundQueryException, 한도 초과는 UpstreamRateLimitedException,
     * 그 외 장애·오류 XML은 UpstreamUnavailableException으로 변환해 던진다.
     */
    fun findDetail(query: SpotDetailQuery): SpotDetail
}

/** 상세 조회 입력. 언어는 요청 스코프에서 결정돼 명시 전달된다(어댑터가 언어별 base-url을 고른다). */
data class SpotDetailQuery(
    val contentId: String,
    val locale: DomainLocale,
)

/**
 * 주변 조회 입력. [category]가 null이면 카테고리 미지정(전체). [pageNo]/[numOfRows]는 upstream 페이지네이션(1-base).
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
 * 주변 조회 결과. [spots]는 필터를 통과한 중립 데이터, [totalCount]는 TourAPI 필터-이전 총계(참고용),
 * [hasMoreUpstreamPages]는 다음 upstream 페이지 존재 여부.
 */
data class NearbySpotResult(
    val spots: List<NearbySpotData>,
    val totalCount: Int?,
    val hasMoreUpstreamPages: Boolean,
)

/**
 * 포트 경계의 중립 데이터. TourAPI 필드명이 아니라 도메인 언어로 표현한다(누출 금지). 거리·정렬은 미포함 —
 * application이 [coordinates]로 거리 계산 후 도메인 Spot으로 조립한다.
 */
data class NearbySpotData(
    val id: String,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val coordinates: Coordinates,
    val category: SpotCategory,
    val copyright: CopyrightType,
)
