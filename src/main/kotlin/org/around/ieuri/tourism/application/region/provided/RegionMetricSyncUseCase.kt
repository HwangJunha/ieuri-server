package org.around.ieuri.tourism.application.region.provided

import java.time.LocalDate
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 동기화(SPEC-0015 R4~R9). 배치가 순회 대상·페이지마다 부르며 호출 하나가 트랜잭션 하나다.
 * - 등록되지 않은 시군구 코드(시도 집계 행 포함)의 행은 적재하지 않고 세기만 한다(R9).
 * - 자연키 upsert라 재실행해도 행이 늘지 않는다(R7).
 */
interface RegionMetricSyncUseCase {

    /** [dataset]의 순회 대상. 순서가 고정이다 — 배치가 진행 위치를 인덱스로 저장한다. */
    fun targets(dataset: RegionMetricDataset): List<RegionMetricTarget>

    /**
     * [target]의 [pageNo] 페이지(1부터)를 받아 적재한다.
     * - 월 지표는 [baseMonth]가 없으면 [runDate]의 달부터 거슬러 올라가 자료가 있는 첫 달을 찾고, 그 달을 결과에 싣는다.
     *   한 번 정한 달은 호출자가 다음 호출에 넘긴다 — 대상마다 다시 찾지 않는다.
     */
    fun syncPage(dataset: RegionMetricDataset, target: RegionMetricTarget, pageNo: Int, runDate: LocalDate, baseMonth: YearMonth?): RegionMetricPageResult
}

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

/** 순회 대상 하나. 방문자수는 지역이 없고, 강도는 시도만, 집중률은 시도와 시군구가 함께 필요하다. */
data class RegionMetricTarget(
    val provinceCode: String?,
    val districtCode: String?,
) {
    companion object {
        /** 지역 없이 한 번 부르는 대상(방문자수). */
        val NATIONWIDE = RegionMetricTarget(provinceCode = null, districtCode = null)
    }
}

/**
 * 한 페이지의 결과. [baseMonth]는 월 지표에서 이번 호출이 쓴(또는 찾은) 기준월이며, 못 찾았으면 null이다.
 * - [skippedUnknownCode]가 크면 정적 시군구 테이블이, [dropped]가 크면 응답 필드명이 낡았다는 신호다.
 */
data class RegionMetricPageResult(
    val written: Int,
    val skippedUnknownCode: Int,
    val dropped: Int,
    val hasMore: Boolean,
    val baseMonth: YearMonth? = null,
)
