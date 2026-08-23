package org.around.ieuri.common.pagination

/**
 * 커서(Relay Connection) 페이지네이션의 공통 읽기 모델. 도메인·프레임워크 무의존인 얇은 공유 커널이다.
 * 기본 페이지네이션은 커서로 통일하고, offset은 페이지번호가 꼭 필요한 화면에서만 별도로 추가한다.
 *
 * @param T 노드(read DTO) 타입.
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
 * 커서는 **조회 결과의 정렬 키**에서 나오므로 노드를 응답용으로 변환한 뒤에 만들면
 * 정렬 키를 잃는다. 그래서 커서를 먼저 확정하고 노드를 나중에 옮긴다.
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

/**
 * 커서 조회 입력. [first]는 가져올 개수, [after]는 이 커서 다음부터(없으면 처음부터).
 * 정렬은 안정적인 단일 키(예: id 내림차순)를 기준으로 keyset 방식으로 수행한다.
 */
data class CursorQuery(
    val first: Int,
    val after: String?,
)

/**
 * 엣지 목록으로 한 페이지를 조립한다. startCursor·endCursor는 엣지에서 도출되므로
 * 호출자가 따로 계산할 필요가 없다 — 손으로 채우면 양 끝이 어긋날 수 있다.
 *
 * [hasPreviousPage]는 "앞 페이지가 실재하는가"가 아니라 **호출자가 커서를 넘겼는가**다.
 * 앞으로만 가는 피드에서는 그 이상을 알 수 없고, 굳이 알아내려면 역방향 조회가 한 번 더 든다.
 *
 * [totalCount]는 기본이 null이다. 전체 개수는 별도 카운트 쿼리를 요구하므로, 화면이 실제로
 * 숫자를 보여줄 때만 넘긴다.
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
 * keyset 조회 결과를 한 페이지로 조립한다. **조회는 `first + 1`건을 읽어 와야 한다** —
 * 초과분의 존재가 곧 다음 페이지의 존재이며, 그것 하나 알자고 별도 카운트 쿼리를 돌지 않는다.
 * 초과분은 응답에서 잘리므로 [cursorOf]는 살아남은 항목에만 적용된다.
 *
 * [hasPreviousPage]는 "앞 페이지가 실재하는가"가 아니라 **호출자가 커서를 넘겼는가**다 —
 * 앞으로만 가는 피드에서는 그 이상을 알 수 없다.
 *
 * @param first 클라이언트가 요청한 개수(상한 적용 후). 이 함수는 클램프하지 않는다.
 * @param after 요청에 실려 온 커서. null이면 첫 페이지다.
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
