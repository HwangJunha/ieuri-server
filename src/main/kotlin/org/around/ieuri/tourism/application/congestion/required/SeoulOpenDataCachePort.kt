package org.around.ieuri.tourism.application.congestion.required

import java.time.Instant

/**
 * 서울 실시간 도시데이터 캐시 포트(SPEC-0014). 저장 시각을 값과 함께 담는다.
 * - 구현체는 저장소 장애를 삼킨다(get→null, put→무시).
 */
interface SeoulOpenDataCachePort {
    /** 구역 1곳의 캐시된 값. 미스면 null. */
    fun get(areaCode: String): CachedSeoulOpenData?

    /** 구역 1곳을 [storedAt] 기준으로 저장한다. */
    fun put(areaCode: String, snapshot: SeoulOpenDataSnapshot, storedAt: Instant)
}

/** 캐시된 값과 그 저장 시각. 신선도는 [storedAt]과 최소 재호출 간격으로 판정한다. */
data class CachedSeoulOpenData(
    val snapshot: SeoulOpenDataSnapshot,
    val storedAt: Instant,
)
