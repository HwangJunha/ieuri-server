package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand
import java.time.LocalDate

/**
 * 관광기후지수 수집 아웃바운드 포트(SPEC-0015 · V49). 배치가 쓰는 수집 경로다.
 *
 * **지역을 순회하지 않는다** — 지역 인자가 선택이라 생략하면 전국이 한 응답에 온다(2026-08-11
 * 실측: 235개 시군구 472행). KTO 집중률과 달리 호출이 1건이다.
 *
 * **업스트림 장애는 도메인 오류로 변환되어 던져진다**(R8) —
 * [org.around.ieuri.tourism.application.exception.UpstreamUnavailableException] ·
 * [org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException].
 * **빈 응답은 장애가 아니다** — 빈 페이지로 정상 반환된다.
 */
interface ClimateIndexSyncPort {

    /**
     * [baseDate]부터 [days]일까지의 전국 관광기후지수.
     *
     * 대상일은 응답 행에 실려 오며 행마다 다르다 — [baseDate]는 조회 시작점일 뿐이다.
     * 반환 코드는 **기상청 코드 그대로다**(변환은 적재 SQL이 한다).
     */
    fun fetchClimateIndices(
        baseDate: LocalDate,
        days: Int,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncClimateIndexCommand>
}
