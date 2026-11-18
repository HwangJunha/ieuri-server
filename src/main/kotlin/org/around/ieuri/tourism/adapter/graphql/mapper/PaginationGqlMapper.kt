package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo

/** 커서 페이지의 경계 정보 → GraphQL `PageInfo`. */
fun CursorPage<*>.toGqlPageInfo(): GqlPageInfo = GqlPageInfo(
    hasNextPage = pageInfo.hasNextPage,
    hasPreviousPage = pageInfo.hasPreviousPage,
    startCursor = pageInfo.startCursor,
    endCursor = pageInfo.endCursor,
)
