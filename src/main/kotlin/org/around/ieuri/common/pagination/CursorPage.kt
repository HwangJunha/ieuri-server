package org.around.ieuri.common.pagination

/**
 * 커서(Relay Connection) 페이지네이션의 공통 읽기 모델.
 */
data class CursorPage<T>(
    val edges: List<CursorEdge<T>>,
    val pageInfo: CursorPageInfo,
    /** 전체 개수(선택). 성능상 생략 가능. */
    val totalCount: Long?,
)

data class CursorEdge<T>(
    val cursor: String,
    val node: T,
)

/**
 * 노드만 다른 타입으로 바꾼다. 커서·pageInfo·totalCount는 그대로다 —
 * 커서는 조회 결과의 정렬 키에서 나오므로 노드 변환 전에 확정한다.
 */
fun <T, R> CursorPage<T>.map(transform: (T) -> R): CursorPage<R> = CursorPage(
    edges = edges.map { CursorEdge(cursor = it.cursor, node = transform(it.node)) },
    pageInfo = pageInfo,
    totalCount = totalCount,
)

data class CursorPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)

/** 커서 조회 입력. [after]가 null이면 처음부터. */
data class CursorQuery(
    val first: Int,
    val after: String?,
)

/**
 * 엣지 목록으로 한 페이지를 조립한다. startCursor·endCursor는 엣지에서 도출한다.
 * - [hasPreviousPage]는 "앞 페이지가 실재하는가"가 아니라 호출자가 커서를 넘겼는가다.
 * - [totalCount]는 기본 null이다.
 */
fun <T> cursorPageOf(
    edges: List<CursorEdge<T>>,
    hasNextPage: Boolean,
    hasPreviousPage: Boolean,
    totalCount: Long? = null,
): CursorPage<T> = CursorPage(
    edges = edges,
    pageInfo = CursorPageInfo(
        hasNextPage = hasNextPage,
        hasPreviousPage = hasPreviousPage,
        startCursor = edges.firstOrNull()?.cursor,
        endCursor = edges.lastOrNull()?.cursor,
    ),
    totalCount = totalCount,
)

/**
 * keyset 조회 결과를 한 페이지로 조립한다.
 * - 조회는 `first + 1`건을 읽어 와야 한다 — 초과분의 존재가 곧 hasNextPage다. 초과분은 잘린다.
 * - [first]는 상한 적용 후 값이다. 이 함수는 클램프하지 않는다.
 * - hasPreviousPage는 [after]가 있는가다.
 */
fun <T> keysetPageOf(
    rows: List<T>,
    first: Int,
    after: String?,
    totalCount: Long? = null,
    cursorOf: (T) -> String,
): CursorPage<T> = cursorPageOf(
    edges = rows.take(first).map { CursorEdge(cursor = cursorOf(it), node = it) },
    hasNextPage = rows.size > first,
    hasPreviousPage = after != null,
    totalCount = totalCount,
)
