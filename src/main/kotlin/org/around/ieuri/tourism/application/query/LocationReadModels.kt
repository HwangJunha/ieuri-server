package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CategorySelection

/**
 * `Query.location`의 read DTO(SPEC-0035 R1). 검증을 통과한 좌표만 담는다 — 보기 필드가 이 값에서
 * 좌표를 읽어 각자 조회한다. id가 없는 가상 노드라 DB를 보지 않는다.
 */
data class LocationView(
    val latitude: Double,
    val longitude: Double,
)

/**
 * `Location.spots`·`Location.recommendedSpots` 공통 입력. 두 보기는 같은 인자를 받는다(POL-new-D).
 * 언어는 Accept-Language, 회원은 인증 컨텍스트에서 온다.
 */
data class LocationViewQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)
