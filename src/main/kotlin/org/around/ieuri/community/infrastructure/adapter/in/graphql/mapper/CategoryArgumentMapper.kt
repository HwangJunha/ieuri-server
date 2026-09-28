package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.error.EmptyCategorySetException
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * GraphQL 카테고리 인자 → 도메인 카테고리 집합(SPEC-0020). 관광지 조회와 같은 규칙이다.
 * - null이면 필터 없음(빈 집합), 빈 배열이면 `EMPTY_CATEGORY_SET`으로 거절한다.
 */
fun toTargetCategories(categories: List<GqlTargetCategory>?): Set<TargetCategory> = when {
    categories == null -> emptySet()
    categories.isEmpty() -> throw EmptyCategorySetException()
    else -> categories.map { TargetCategory.valueOf(it.name) }.toSet()
}
