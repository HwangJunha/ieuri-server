package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.application.region.required.dto.DistrictRegion

/** 적재 대상 시군구·시도 코드 조회 포트. */
interface DistrictCodeQueryPort {
    /**
     * 순회 대상 시군구 전체(집중률용).
     * - 코드 오름차순 고정. 배치가 진행 위치를 인덱스로 저장하므로 순서가 바뀌면 재개가 어긋난다.
     */
    fun findAllRegions(): List<DistrictRegion>

    /** 순회 대상 시도 코드 전체(중복 없음, 강도 2종용). 오름차순 고정 — [findAllRegions]와 같은 이유. */
    fun findAllProvinceCodes(): List<String>
}
