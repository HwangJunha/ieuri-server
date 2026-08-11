package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.command.SyncVisitorCountCommand

/**
 * 한국관광공사 관광통계 4종 적재 아웃바운드 포트(SPEC-0015 R7).
 *
 * 네 upsert 모두 자연키 충돌 시 값을 덮어쓴다 — **재실행해도 행이 중복되지 않는다.**
 * 멱등성이 여기서만 성립하므로 구현이 upsert가 아니면 R7이 깨진다.
 */
interface TourStatsStorePort {

    fun upsertVisitorCounts(records: List<SyncVisitorCountCommand>): Int

    fun upsertConcentrationRates(records: List<SyncConcentrationRateCommand>): Int

    fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int

    fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int
}

/**
 * 적재된 시군구 코드 집합 조회 포트(R9).
 *
 * KTO 응답에는 우리 정적 테이블에 없는 코드가 섞일 수 있고, 그 행은 건너뛰고 배치는 계속해야
 * 한다. `sigungu`에 FK를 걸지 않은 이유가 이것이며(V44 주석) 유효성 확인이 여기로 온다.
 */
interface SigunguCodeQueryPort {
    /** 등록된 시군구 코드 전체. 배치 실행당 한 번만 부른다. */
    fun findAllCodes(): Set<String>
}
