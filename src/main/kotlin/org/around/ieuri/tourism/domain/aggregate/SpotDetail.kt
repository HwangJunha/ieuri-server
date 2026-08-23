package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 상세 본문(도메인 모델). Spot의 확장이 아니라 상세 화면 전용 read shape이며, 순수 Kotlin이다.
 *
 * **detailCommon2가 주는 본문만 담는다** — 갤러리·운영정보·이용안내는 별도 오퍼레이션으로 필드별
 * 지연 조회되므로 여기 추가하면 그 지연 조회가 무의미해진다. 식별자(`spot.id`)도 담지 않는다
 * (읽기 DTO `SpotDetailView`가 소유한다).
 *
 * [likeCount]는 어댑터가 채우지 않는다 — application이 [withLikeCount]로 실시간 집계값을 얹으며,
 * 기본값 0은 "아직 집계 전" 상태다.
 */
data class SpotDetail(
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    val likeCount: Long = 0,
) {
    init {
        require(title.isNotBlank()) { "title은 비어 있을 수 없습니다." }
        require(thumbnailImageUrl.startsWith("https://")) { "썸네일 URL은 https로 정규화되어야 합니다." }
        require(likeCount >= 0) { "좋아요 수는 음수일 수 없습니다." }
    }

    /** 항목 저작권 표준 문구(전문). copyright 유형에서 도출(별도 저장 없음). */
    val attributionText: String get() = copyright.attributionText

    /** 실시간 집계한 좋아요 수를 얹은 복제본. TourAPI 파생 필드는 캐시 대상이나 likeCount는 캐시하지 않는다(§5). */
    fun withLikeCount(likeCount: Long): SpotDetail = copy(likeCount = likeCount)

    companion object {
        /** 갤러리 최대 장수(AC-8). 갤러리는 별도 조회지만 상한은 상세의 계약이라 여기 둔다. */
        const val MAX_GALLERY_SIZE = 10
    }
}
