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
