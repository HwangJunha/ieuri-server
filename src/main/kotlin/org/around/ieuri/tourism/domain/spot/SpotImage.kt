package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.shared.CopyrightType

/**
 * 갤러리 이미지 1장(값 객체). 저작권은 이미지별이며 부모 SpotDetail에서 상속하지 않는다.
 * - [imageUrl]은 https 정규화된 값이어야 한다.
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
