package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.error.EmptyCategorySetException
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * GraphQL 카테고리 인자 → [CategorySelection](SPEC-0020 R5). 모든 조회 연산이 이 함수를 지난다.
 * - 생략·null이면 필터 없음, 빈 배열은 EMPTY_CATEGORY_SET으로 거절된다.
 */
fun toCategorySelection(categories: List<String>?): CategorySelection = CategorySelection.of(
    categories = categories?.map { SpotCategory(it) },
    onEmpty = { EmptyCategorySetException() },
)
