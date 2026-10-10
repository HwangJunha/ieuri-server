package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 좋아요 목록 조회 입력. [after]는 (likedAt, spotId)에서 파생된 불투명 커서다. */
data class LikedSpotsSearch(
    val memberId: Long,
    val language: DomainLocale,
    /** 비어 있으면 전체. */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)
