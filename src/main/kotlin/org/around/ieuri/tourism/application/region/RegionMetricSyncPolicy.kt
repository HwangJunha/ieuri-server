package org.around.ieuri.tourism.application.region

/**
 * KTO 빅데이터 동기화의 정책 값(SPEC-0015). 전부 설정에서 온다(POL-044).
 *
 * @param pageSize 페이지당 행 수. 낮추면 집중률(시군구 252회) 호출이 배가돼 일 한도에 걸린다.
 * @param visitorBackfillDays 매 실행이 다시 받아올 방문자수 구간의 길이(일). 실패한 날의 결손을 다음 실행이 메운다(R7).
 * @param monthlyLookbackMonths 월 지표에서 이번 달부터 거슬러 올라가며 자료가 있는 달을 찾는 최대 개월(R8).
 */
data class RegionMetricSyncPolicy(
    val pageSize: Int,
    val visitorBackfillDays: Long,
    val monthlyLookbackMonths: Int,
)
