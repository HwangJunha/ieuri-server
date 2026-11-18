package org.around.ieuri.tourism.application.region.provided.dto

/** KTO 빅데이터 지표 4종. 갱신 주기가 달라 잡이 따로다. */
enum class RegionMetricDataset {
    /** 시군구 일별 방문자수. 지역 순회 없이 날짜 구간으로 전 지역이 온다. */
    VISITOR_COUNT,

    /** 관광지 집중률 예측. 시군구마다 부른다. */
    CONCENTRATION_RATE,

    /** 월 체류 강도. 시도마다 부른다. */
    STAY_INTENSITY,

    /** 월 소비 강도. 시도마다 부른다. */
    SPENDING_INTENSITY,
}
