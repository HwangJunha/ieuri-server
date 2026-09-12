package org.around.ieuri.tourism.application.port.out.cache

import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult

/**
 * 추천 목록 묶음 캐시(SPEC-0035 R17). **복사본이다** — 없어도, 틀려도 같은 결과가 재계산된다.
 * 값은 순열 순서의 카드 목록이다. id만 두면 이어 받을 때 거리·제목을 다시 조회해야 해서
 * 캐시 히트가 DB 왕복을 없애지 못한다.
 *
 * **실패는 예외로 올리고 서비스가 미스·no-op으로 흡수한다** — `ValkeySpotCache`(어댑터가 삼킴)와
 * 다른 배치인 이유는 AC-17.3("포트가 예외를 던져도 재계산")을 포트 스텁만으로 고정하기 위해서다.
 */
interface RecommendedSpotsBatchCachePort {
    /** 조건 해시 [key]의 [batchNo]번째 묶음. 미스·실패는 null. */
    fun get(key: String, batchNo: Int): List<SpotSearchResult>?

    fun put(key: String, batchNo: Int, cards: List<SpotSearchResult>)
}
