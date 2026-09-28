package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.error.EmptyCategorySetException
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * GraphQL 카테고리 인자 → 도메인 카테고리 집합(SPEC-0020).
 *
 * 해석 규칙은 관광지 조회(`spots`·`searchSpots` 등)와 **같다** — 생략·null이면 필터 없음,
 * 빈 배열은 `EMPTY_CATEGORY_SET`으로 거절. 같은 개념을 두 도메인이 다르게 해석하면
 * 클라이언트가 화면마다 다른 규칙을 외워야 한다.
 *
 * 관광지 쪽 코드는 열린 문자열이지만 커뮤니티의 `TargetCategory`는 닫힌 enum이라,
 * 스키마 검증이 알 수 없는 값을 먼저 걸러낸다.
 */
fun toTargetCategories(categories: List<GqlTargetCategory>?): Set<TargetCategory> = when {
    categories == null -> emptySet()
    categories.isEmpty() -> throw EmptyCategorySetException()
    else -> categories.map { TargetCategory.valueOf(it.name) }.toSet()
}
