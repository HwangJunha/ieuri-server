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
 * 관광 정보 조회 아웃바운드 포트. TourAPI 세부를 해소한 중립 데이터를 돌려준다.
 * - 거리 계산·정렬·커서·도메인 조립은 application이 한다.
 */
interface TourInfoPort {
    /** 주변 관광지 한 upstream 페이지(upstream 순서). 장애는 UpstreamUnavailableException. */
    fun findNearby(query: NearbySpotQuery): NearbySpotResult

    /**
     * 상세 본문. 갤러리·운영정보·이용안내·[SpotDetail.likeCount]는 채우지 않는다.
     * - 본문이 없으면 예외가 아니라 null이다(SPEC-0008).
     * - 장애는 예외다: 한도 초과는 UpstreamRateLimitedException, 그 외는 UpstreamUnavailableException.
     */
    fun findDetail(query: SpotDetailQuery): SpotDetail?

    /** 갤러리. 없으면 빈 목록. */
    fun findGallery(query: SpotDetailQuery): List<SpotImage>

    /** 운영 정보. 표시할 값이 없거나 [category]에 해당하는 언어별 코드가 없으면 null. */
    fun findIntro(query: SpotDetailQuery, category: SpotCategory): SpotIntro?

    /** 이용안내 항목. [category]에 해당하는 언어별 코드가 없으면 빈 목록. */
    fun findInfoItems(query: SpotDetailQuery, category: SpotCategory): List<SpotInfoItem>
}

/** 상세 조회 입력. */
data class SpotDetailQuery(
    val contentId: String,
    val locale: DomainLocale,
)

/** 주변 조회 입력. [category]가 null이면 전체. [pageNo]는 1부터. */
data class NearbySpotQuery(
    val locale: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val pageNo: Int,
    val numOfRows: Int,
)

/** 주변 조회 결과. [totalCount]는 필터 이전 upstream 총계(참고용). */
data class NearbySpotResult(
    val spots: List<NearbySpotData>,
    val totalCount: Int?,
    val hasMoreUpstreamPages: Boolean,
)

/** 포트 경계의 중립 데이터. 거리는 포함하지 않는다. */
data class NearbySpotData(
    val id: String,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val coordinates: Coordinates,
    val category: SpotCategory,
    val copyright: CopyrightType,
)
