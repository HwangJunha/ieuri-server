package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.tourism.application.shared.required.DistrictRegion
import java.time.LocalDate
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 동기화(SPEC-0015 R4~R9). 배치가 순회 단위·페이지마다 부르며 호출 하나가 트랜잭션 하나다.
 * - 등록되지 않은 시군구 코드(시도 집계 행 포함)의 행은 적재하지 않고 세기만 한다(R9).
 * - 자연키 upsert라 재실행해도 행이 늘지 않는다(R7).
 */
interface RegionMetricSyncUseCase {

    /** 집중률 순회 대상 시군구. 코드 오름차순 고정 — 배치가 진행 위치를 인덱스로 저장한다. */
    fun districts(): List<DistrictRegion>

    /** 강도 2종 순회 대상 시도 코드. 오름차순 고정. */
    fun provinces(): List<String>

    /** [startDate]~[endDate](양끝 포함)의 시군구 방문자수 한 페이지. */
    fun syncVisitorCounts(startDate: LocalDate, endDate: LocalDate, pageNo: Int, pageSize: Int): RegionMetricPageResult

    /** 시군구 하나의 집중률 예측 한 페이지. */
    fun syncConcentrationRates(district: DistrictRegion, pageNo: Int, pageSize: Int): RegionMetricPageResult

    /** 시도 하나의 [month] 체류 강도 한 페이지. */
    fun syncStayIntensities(provinceCode: String, month: YearMonth, pageNo: Int, pageSize: Int): RegionMetricPageResult

    /** 시도 하나의 [month] 소비 강도 한 페이지. */
    fun syncSpendingIntensities(provinceCode: String, month: YearMonth, pageNo: Int, pageSize: Int): RegionMetricPageResult
}

/**
 * 한 페이지의 결과. [totalCount]는 상류가 보고한 총계로 다음 페이지 판정과 월 탐색에 쓴다.
 * - [skippedUnknownCode]가 크면 정적 시군구 테이블이, [dropped]가 크면 응답 필드명이 낡았다는 신호다.
 */
data class RegionMetricPageResult(
    val written: Int,
    val skippedUnknownCode: Int,
    val dropped: Int,
    val totalCount: Int,
) {
    fun hasMore(pageNo: Int, pageSize: Int): Boolean = pageNo.toLong() * pageSize.toLong() < totalCount.toLong()
}
