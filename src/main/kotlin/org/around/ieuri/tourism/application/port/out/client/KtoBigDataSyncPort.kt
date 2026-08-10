package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.command.SyncVisitorCountCommand
import java.time.LocalDate
import java.time.YearMonth

/**
 * KTO 빅데이터 4종 수집 아웃바운드 포트(SPEC-0015 R4~R6). 배치가 쓰는 수집 경로다.
 *
 * **지역별로 나눠 호출할 수 없다** — 방문자수 API에 지역 필터 파라미터가 없어(spec §4.3)
 * 날짜를 주면 전 지역이 한 번에 온다. 따라서 인자는 기간과 페이지뿐이다.
 *
 * **업스트림 장애는 도메인 오류로 변환되어 던져진다**(R8) —
 * [org.around.ieuri.tourism.application.exception.UpstreamUnavailableException] ·
 * [org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException].
 * **빈 응답은 장애가 아니다** — 빈 페이지로 정상 반환된다.
 */
interface KtoBigDataSyncPort {

    /** [baseDate] 하루치 시군구 방문자수. */
    fun fetchVisitorCounts(baseDate: LocalDate, pageNo: Int, numOfRows: Int): KtoSyncPage<SyncVisitorCountCommand>

    /** [baseDate] 기준으로 발표된 향후 30일 집중률 예측(R5). 행마다 대상일이 다르다. */
    fun fetchConcentrationRates(
        baseDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): KtoSyncPage<SyncConcentrationRateCommand>

    /** [baseMonth]의 체류 강도. */
    fun fetchStayIntensities(baseMonth: YearMonth, pageNo: Int, numOfRows: Int): KtoSyncPage<SyncDemandIntensityCommand>

    /** [baseMonth]의 소비 강도. 외지인 소비이며 외국인 소비 지표는 없다(spec §4.7). */
    fun fetchSpendingIntensities(
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): KtoSyncPage<SyncDemandIntensityCommand>
}

/**
 * 수집된 한 페이지.
 *
 * [droppedCount]는 필수 필드 누락·숫자 파싱 실패로 어댑터가 버린 행 수다(관측용). 응답 스키마가
 * 문서로 확정되지 않아, 이 값이 크면 데이터가 아니라 **우리 DTO의 필드명이 틀렸다는 신호다.**
 */
data class KtoSyncPage<T>(
    val records: List<T>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /** 업스트림에 다음 페이지가 남았는지. 수집 루프의 종료 조건. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean =
        pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
