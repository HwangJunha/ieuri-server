package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 상세 본문(도메인 모델). 본문만 담으며 식별자는 읽기 DTO `SpotDetailView`가 소유한다.
 * - [likeCount]는 [withLikeCount]로 얹으며, 기본값 0은 "아직 집계 전" 상태다.
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

    /** 실시간 집계한 좋아요 수를 얹은 복제본. likeCount는 캐시하지 않는다. */
    fun withLikeCount(likeCount: Long): SpotDetail = copy(likeCount = likeCount)

    companion object {
        /** 갤러리 최대 장수(AC-8). */
        const val MAX_GALLERY_SIZE = 10
    }
}
