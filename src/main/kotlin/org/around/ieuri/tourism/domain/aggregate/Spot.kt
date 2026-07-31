package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.Distance
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 도메인 모델. TourAPI 응답이 아니라 **어댑터가 이미 필터·정규화·매핑을 끝낸** 순수 도메인 표현이다.
 * 도메인은 TourAPI 필드명(contentid/firstimage/mapx…)·코드 체계를 알지 않는다 — 어댑터가 이 모델로 번역한다.
 * DB에 적재하지 않는 읽기 전용 값(관광 데이터는 저장하지 않음). JPA 엔티티가 아니다.
 *
 * [distance]는 application이 사용자 좌표 기준으로 채운다(도메인 [Distance.between] 사용). 서버 정렬 기준.
 */
data class Spot(
    /** TourAPI contentId. 같은 장소라도 언어별로 다르다 — 요청 언어 내에서만 안정적. */
    val id: String,
    val title: String,
    /** addr1/addr2 병합 주소(없을 수 있음). 원본 그대로 노출(보정하지 않음). */
    val address: String?,
    /** https 정규화된 카드 이미지. 항상 채워짐(이미지 없는 항목은 어댑터가 드랍). */
    val thumbnailImageUrl: String,
    /** 원 좌표(경도=mapX, 위도=mapY). */
    val coordinates: Coordinates,
    /** 사용자 좌표 기준 직선거리. */
    val distance: Distance,
    val category: SpotCategory,
    val copyright: CopyrightType,
    /** 공공누리 출처표시 문구. UNSPECIFIED여도 최소 출처("한국관광공사/공공누리")를 담는다(AC-15). */
    val attributionText: String,
) {
    init {
        require(id.isNotBlank()) { "spot id는 필수입니다." }
        require(title.isNotBlank()) { "spot title은 필수입니다." }
        require(thumbnailImageUrl.startsWith("https://")) { "thumbnailImageUrl은 https여야 합니다." }
        require(attributionText.isNotBlank()) { "attributionText는 비어 있을 수 없습니다." }
    }
}
