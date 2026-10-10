package org.around.ieuri.community.application.post.provided.dto

import org.around.ieuri.community.domain.post.PostSortOrder
import org.around.ieuri.community.domain.post.TargetCategory

/**
 * 글 피드 조회 입력. 인자 조합으로 범위가 정해진다 — 대상 하나, 좌표 반경, 또는 전체다.
 * - [radiusMeters]는 좌표를 줄 때만 의미가 있다.
 */
data class PostsQuery(
    /** 이 장소(카탈로그)에 붙은 글만. null이면 장소로 좁히지 않는다. */
    val catalogId: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020). */
    val categories: Set<TargetCategory>,
    /** 태그 필터. 정규화해 매칭하므로 대소문자를 가리지 않는다. */
    val tag: String?,
    /** 정렬 방식. 좌표가 없으면 DISTANCE도 LATEST로 떨어진다. */
    val sort: PostSortOrder,
    /** true면 숨긴 글만 본다(해제 화면). 기본은 false로 숨긴 글을 제외한다. */
    val hiddenByViewer: Boolean = false,
    val first: Int,
    val after: String?,
) {
    /** 좌표가 둘 다 있어야 반경 조회다. 하나만 주면 좌표를 안 준 것으로 본다. */
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}
