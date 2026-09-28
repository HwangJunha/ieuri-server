package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.command.SyncVisitorCountCommand
import java.time.LocalDate
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 수집 아웃바운드 포트(SPEC-0015 R4~R6). 배치가 쓰는 수집 경로다.
 *
 * **호출 단위가 종마다 다르다**(2026-08-11 실측):
 *  - 방문자수만 지역 필터가 없어 날짜만 주면 전 지역이 온다(spec §4.3).
 *  - 강도 2종은 `areaCd`(시도)만으로 그 시도의 시군구 전체가 온다. 따라서 17개 시도만 순회한다.
 *  - 집중률만 `signguCd`가 **추가로 필수**다. 빠지면 `NO_MANDATORY_REQUEST_PARAMETERS_ERROR1`이
 *    오므로 252개 시군구를 순회해야 한다.
 *
 * **업스트림 장애는 도메인 오류로 변환되어 던져진다**(R8) —
 * [org.around.ieuri.tourism.application.exception.UpstreamUnavailableException] ·
 * [org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException].
 * **빈 응답은 장애가 아니다** — 빈 페이지로 정상 반환된다.
 */
interface TourStatsSyncPort {

    /**
     * [startDate]~[endDate] 구간의 시군구 방문자수(양끝 포함).
     *
     * **이 종만 지역 인자가 없다** — API에 지역 필터가 없어 날짜만 주면 전 지역이 온다.
     *
     * **하루씩 부르지 않고 구간으로 받는다** — API가 날짜 범위를 지원하므로 30일치가 호출
     * 1건이다. 매 실행이 최근 구간을 통째로 다시 받아 upsert하면, 배치가 며칠 실패해도
     * 다음 성공 실행이 구멍을 메운다.
     */
    fun fetchVisitorCounts(
        startDate: LocalDate,
        endDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncVisitorCountCommand>

    /**
     * [areaCd] 시도 · [signguCd] 시군구의 집중률 예측(R5).
     *
     * **날짜 인자가 없다** — 이 오퍼레이션은 어떤 날짜 파라미터도 받지 않으며(`baseYmd` ·
     * `startYmd` · `baseYm` 모두 `INVALID_REQUEST_PARAMETER_ERROR`), 한 시군구를 요청하면
     * 향후 예측일이 전부 온다. 대상일은 응답 행의 `baseYmd`에 실려 행마다 다르다.
     */
    fun fetchConcentrationRates(
        areaCd: String,
        signguCd: String,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncConcentrationRateCommand>

    /**
     * [areaCd] 시도의 [baseMonth] 체류 강도. 월 인자는 `baseYm` 하나다(구간 아님).
     *
     * **`signguCd`를 받지 않는다**(2026-08-11 실측) — `areaCd`만 주면 그 시도의 시군구 전체가
     * 한 응답에 온다(서울 26행). 행마다 `signguCd`가 실려 오므로 구분은 응답에서 한다.
     */
    fun fetchStayIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand>

    /**
     * [areaCd] 시도의 [baseMonth] 소비 강도. 외지인 소비이며 외국인 소비 지표는 없다(spec §4.7).
     * 체류 강도와 같이 `signguCd` 없이 시도 단위로 온다.
     */
    fun fetchSpendingIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand>
}

/**
 * 수집된 한 페이지.
 *
 * [droppedCount]는 필수 필드 누락·숫자 파싱 실패로 어댑터가 버린 행 수다(관측용). 응답 스키마가
 * 문서로 확정되지 않아, 이 값이 크면 데이터가 아니라 **우리 DTO의 필드명이 틀렸다는 신호다.**
 */
data class TourStatsSyncPage<T>(
    val records: List<T>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /** 업스트림에 다음 페이지가 남았는지. 수집 루프의 종료 조건. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean = pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
