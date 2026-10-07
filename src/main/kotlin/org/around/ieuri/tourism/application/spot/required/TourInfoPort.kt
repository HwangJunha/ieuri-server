package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro

/**
 * 관광 정보 조회 아웃바운드 포트. TourAPI 세부를 해소한 중립 데이터를 돌려준다.
 * - 상세의 갈래(본문·갤러리·운영 정보·이용안내)마다 따로 부른다 — 선택된 필드만 업스트림을 친다.
 */
interface TourInfoPort {
    /**
     * 상세 본문. 갤러리·운영정보·이용안내는 채우지 않는다.
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
