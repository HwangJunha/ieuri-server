package org.around.ieuri.tourism.domain.vo

/**
 * 갤러리 이미지 1장(값 객체). TourAPI detailImage2가 이미지별 cpyrhtDivCd를 주므로 저작권/문구는 이미지별이며
 * 부모 SpotDetail에서 상속하지 않는다. [attributionText]는 저장하지 않고 [copyright]에서 도출한다.
 *
 * [imageUrl]은 항상 https 정규화된 값이어야 한다(url 없는 항목은 애초에 목록에 담지 않음 — 어댑터 책임).
 */
data class SpotImage(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String?,
    val copyright: CopyrightType,
) {
    init {
        require(imageUrl.startsWith("https://")) { "이미지 URL은 https로 정규화되어야 합니다." }
    }

    /** 이미지별 저작권 표준 문구. copyright 유형에서 도출(별도 저장 없음). */
    val attributionText: String get() = copyright.attributionText
}
