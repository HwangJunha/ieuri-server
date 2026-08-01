package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

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
     * 상세 본문(detailCommon2 1콜). GraphQL 루트 spotDetail이 부른다. 갤러리·운영정보·이용안내는 포함하지 않는다 —
     * 각 필드가 요청될 때만 아래 메서드로 따로 가져온다(필드별 지연 조회).
     * [SpotDetail.likeCount]도 여기서 채우지 않는다(application이 실시간 집계로 얹음).
     *
     * upstream이 해소하지 못하면 SpotDetailNotFoundException, 한도 초과는 UpstreamRateLimitedException,
     * 그 외 장애·오류 XML은 UpstreamUnavailableException으로 변환해 던진다.
     */
    fun findDetail(query: SpotDetailQuery): SpotDetail

    /** 갤러리(detailImage2 1콜). gallery 필드가 요청될 때만 호출된다. 없으면 빈 목록. */
    fun findGallery(query: SpotDetailQuery): List<SpotImage>

    /**
     * 운영 정보(detailIntro2 1콜). intro 필드가 요청될 때만 호출된다.
     * 표시할 값이 하나도 없거나 [category]에 해당하는 언어별 코드가 없으면 null.
     */
    fun findIntro(query: SpotDetailQuery, category: SpotCategory): SpotIntro?

    /**
     * 이용안내 항목(detailInfo2 1콜). infoItems 필드가 요청될 때만 호출된다.
     * [category]에 해당하는 언어별 코드가 없으면 빈 목록.
     */
    fun findInfoItems(query: SpotDetailQuery, category: SpotCategory): List<SpotInfoItem>
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
