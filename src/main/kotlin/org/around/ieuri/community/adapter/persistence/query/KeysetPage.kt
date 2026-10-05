package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.jetbrains.exposed.v1.core.ResultRow

/** `first + 1`건을 읽은 결과를 한 페이지로. 초과분의 존재가 hasNextPage이고, hasPreviousPage는 커서를 받았는가다. */
fun <T> page(rows: List<ResultRow>, first: Int, hasCursor: Boolean, edgeOf: (ResultRow) -> CursorEdge<T>): CursorPage<T> = cursorPageOf(rows.take(first).map(edgeOf), hasNextPage = rows.size > first, hasPreviousPage = hasCursor)
