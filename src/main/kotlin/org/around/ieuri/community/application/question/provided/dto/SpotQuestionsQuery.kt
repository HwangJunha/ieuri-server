package org.around.ieuri.community.application.question.provided.dto

/**
 * 질문 목록 조회 입력. 정렬은 고정이다(POL-019).
 * - 세 필터는 독립적이며 함께 걸린다. 전부 비면 전체 질문이 대상이다.
 */
data class SpotQuestionsQuery(
    /** 이 장소의 질문만. null이면 장소로 좁히지 않는다. */
    val catalogId: Long?,
    /** true면 호출자가 쓴 질문만. 비로그인에게는 허용되지 않는다(리졸버가 막는다). */
    val mine: Boolean = false,
    /** 이 id들만. null이면 id로 좁히지 않는다. 빈 목록은 "결과 없음"이다(전체가 아니다). */
    val ids: List<Long>? = null,
    val first: Int,
    val after: String?,
)
