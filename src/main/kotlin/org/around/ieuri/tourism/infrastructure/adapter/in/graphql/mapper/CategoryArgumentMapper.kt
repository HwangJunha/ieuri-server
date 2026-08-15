package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.exception.EmptyCategorySetException
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * GraphQL 카테고리 인자 → [CategorySelection] 변환(SPEC-0020 R5). 5개 조회 연산이 모두 이 함수를
 * 지나므로 해석 규칙이 연산마다 어긋나지 않는다.
 *
 * 생략되거나 null이면 필터 없음이고, 빈 배열은 EMPTY_CATEGORY_SET으로 거절된다.
 */
fun toCategorySelection(categories: List<String>?): CategorySelection = CategorySelection.of(
    categories = categories?.map { SpotCategory(it) },
    onEmpty = { EmptyCategorySetException() },
)
