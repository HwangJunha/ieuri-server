package org.around.ieuri.tourism.application.shared.required

import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncVisitorCountCommand

/**
 * 한국관광공사 관광통계 4종 적재 아웃바운드 포트(SPEC-0015).
 * - 자연키 충돌 시 값을 덮어쓴다. 재실행 멱등성은 여기서만 성립한다.
 */
interface RegionMetricStorePort {

    fun upsertVisitorCounts(records: List<SyncVisitorCountCommand>): Int

    fun upsertConcentrationRates(records: List<SyncConcentrationRateCommand>): Int

    fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int

    fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int
}

/** 적재 대상 시군구·시도 코드 조회 포트. 등록되지 않은 코드의 행을 거르는 데 쓴다. */
interface DistrictCodeQueryPort {
    /** 등록된 시군구 코드 전체. 배치 실행당 한 번만 부른다. */
    fun findAllCodes(): Set<String>

    /**
     * 순회 대상 시군구 전체(집중률용).
     * - 코드 오름차순 고정. 배치가 진행 위치를 인덱스로 저장하므로 순서가 바뀌면 재개가 어긋난다.
     */
    fun findAllRegions(): List<DistrictRegion>

    /** 순회 대상 시도 코드 전체(중복 없음, 강도 2종용). 오름차순 고정 — [findAllRegions]와 같은 이유. */
    fun findAllProvinceCodes(): List<String>
}

/**
 * 순회용 시군구 한 건. [code]는 5자리, [provinceCode]는 2자리.
 * - 집중률 요청은 둘 다 필수다. 하나만 보내면 `NO_MANDATORY_REQUEST_PARAMETERS_ERROR1`이 온다.
 */
data class DistrictRegion(
    val code: String,
    val provinceCode: String,
)
