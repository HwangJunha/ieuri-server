package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand

/**
 * 관광기후지수 적재 아웃바운드 포트(SPEC-0015 R7 · V49).
 *
 * 자연키 (district_code, forecast_date) 충돌 시 값을 덮어쓴다 — **재실행해도 행이 중복되지
 * 않는다.** 멱등성이 여기서만 성립하므로 구현이 upsert가 아니면 R7이 깨진다.
 *
 * **구현이 기상청 코드 → 행정표준코드 매핑까지 책임진다.** 명령의 `districtCode`는 기상청
 * 코드이며 매핑되지 않는 코드도 그대로 적재된다(V49가 FK를 걸지 않은 이유).
 */
interface ClimateIndexStorePort {

    /** @return 실제로 반영된 행 수. */
    fun upsertClimateIndices(records: List<SyncClimateIndexCommand>): Int
}
