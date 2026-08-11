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
 * 적재된 시군구 코드 조회 포트(R9 · 지역 순회).
 *
 * KTO 응답에는 우리 정적 테이블에 없는 코드가 섞일 수 있고, 그 행은 건너뛰고 배치는 계속해야
 * 한다. `sigungu`에 FK를 걸지 않은 이유가 이것이며(V44 주석) 유효성 확인이 여기로 온다.
 */
interface SigunguCodeQueryPort {
    /** 등록된 시군구 코드 전체. 배치 실행당 한 번만 부른다. */
    fun findAllCodes(): Set<String>

    /**
     * 순회 대상 시군구 전체. **집중률만** 시군구마다 호출해야 해서 필요하다.
     *
     * **코드 오름차순으로 고정한다** — 배치가 ExecutionContext에 진행 위치를 인덱스로 저장하므로,
     * 순서가 실행마다 바뀌면 재실행이 엉뚱한 지역에서 이어진다.
     */
    fun findAllRegions(): List<SigunguRegion>

    /**
     * 순회 대상 시도 코드 전체(중복 없음, 17건). 강도 2종이 `areaCd`만으로 시도의 시군구
     * 전체를 받아오므로 252가 아니라 이 목록을 돈다(2026-08-11 실측).
     *
     * **오름차순으로 고정한다** — [findAllRegions]와 같은 이유(재개 인덱스).
     */
    fun findAllProvinceCodes(): List<String>
}

/**
 * 순회용 시군구 한 건.
 *
 * @param code 시군구 코드 5자리. 집중률 요청의 `signguCd`로 나간다.
 * @param provinceCode 상위 시도 코드 2자리. 요청의 `areaCd`로 나간다 — **집중률은 둘 다 필수라**
 *   하나만 보내면 `NO_MANDATORY_REQUEST_PARAMETERS_ERROR1`이 온다.
 */
data class SigunguRegion(
    val code: String,
    val provinceCode: String,
)
