package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection

/** 좋아요 목록 조회 입력(SPEC-0008). 언어는 Accept-Language, 회원은 인증 컨텍스트에서 온다. */
data class LikedSpotsQuery(
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)
