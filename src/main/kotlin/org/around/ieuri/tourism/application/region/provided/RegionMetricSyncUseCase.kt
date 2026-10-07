package org.around.ieuri.tourism.application.region.provided

import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricPageResult
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricTarget
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
