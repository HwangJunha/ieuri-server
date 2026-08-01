package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage

/**
 * 관광지 상세(도메인 모델). Spot의 확장이 아니라 상세 화면 전용 read shape다.
 *
 * TourAPI 파생 필드(overview·gallery·연락처·copyright)는 어댑터가 이미 정제(2콜 병합·https 정규화·10장 컷·빈문자열
 * null화·저작권 조립)를 끝낸 상태로 넘긴다. [likeCount]는 캐시 경계 분리를 위해 어댑터가 채우지 않고, application이
 * [withLikeCount]로 실시간 집계값을 얹는다(SPEC-0003 §4·§5). 기본값 0은 "아직 집계 전" 상태다.
 *
 * 순수 Kotlin — 프레임워크/JPA/DGS 의존 없음.
 */
data class SpotDetail(
    val id: String,
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    val gallery: List<SpotImage>,
    val likeCount: Long = 0,
) {
    init {
        require(id.isNotBlank()) { "contentId는 비어 있을 수 없습니다." }
        require(title.isNotBlank()) { "title은 비어 있을 수 없습니다." }
        require(thumbnailImageUrl.startsWith("https://")) { "썸네일 URL은 https로 정규화되어야 합니다." }
        require(gallery.size <= MAX_GALLERY_SIZE) { "갤러리는 최대 ${MAX_GALLERY_SIZE}장입니다." }
        require(likeCount >= 0) { "좋아요 수는 음수일 수 없습니다." }
    }

    /** 항목 저작권 표준 문구(전문). copyright 유형에서 도출(별도 저장 없음). */
    val attributionText: String get() = copyright.attributionText

    /** 실시간 집계한 좋아요 수를 얹은 복제본. TourAPI 파생 필드는 캐시 대상이나 likeCount는 캐시하지 않는다(§5). */
    fun withLikeCount(likeCount: Long): SpotDetail = copy(likeCount = likeCount)

    companion object {
        const val MAX_GALLERY_SIZE = 10
    }
}
