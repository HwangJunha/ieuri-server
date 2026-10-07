package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.application.region.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.region.required.dto.RegionMetricSyncPage
import java.time.LocalDate
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 수집 아웃바운드 포트(SPEC-0015 R4~R6). 호출 단위가 종마다 다르다.
 * - 업스트림 장애는 [org.around.ieuri.tourism.application.shared.exception.UpstreamUnavailableException] ·
 *   [org.around.ieuri.tourism.application.shared.exception.UpstreamRateLimitedException]으로 던진다(R8).
 * - 빈 응답은 장애가 아니라 빈 페이지로 반환된다.
 */
interface RegionMetricSyncPort {

    /**
     * [startDate]~[endDate] 구간의 시군구 방문자수(양끝 포함).
     * - 지역 인자가 없다 — 날짜만 주면 전 지역이 온다.
     */
    fun fetchVisitorCounts(
        startDate: LocalDate,
        endDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncVisitorCountCommand>

    /**
     * [areaCd] 시도 · [signguCd] 시군구의 집중률 예측(R5). `signguCd`가 필수다.
     * - 날짜 인자가 없다 — 향후 예측일이 전부 오며, 대상일은 행마다 `baseYmd`에 실린다.
     */
    fun fetchConcentrationRates(
        areaCd: String,
        signguCd: String,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncConcentrationRateCommand>

    /**
     * [areaCd] 시도의 [baseMonth] 체류 강도.
     * - `signguCd`를 받지 않는다 — 그 시도의 시군구 전체가 오고, 행마다 `signguCd`가 실린다.
     */
    fun fetchStayIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncDemandIntensityCommand>

    /**
     * [areaCd] 시도의 [baseMonth] 소비 강도(외지인 소비. 외국인 소비 지표는 없다).
     * - 체류 강도처럼 `signguCd` 없이 시도 단위로 온다.
     */
    fun fetchSpendingIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncDemandIntensityCommand>
}
