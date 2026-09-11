package org.around.ieuri.tourism.application.port.out.cache

import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult

/**
 * 추천 피드 묶음 캐시(SPEC-0035 R17). **복사본이다** — 없어도, 틀려도 같은 결과가 재계산된다.
 * 값은 순열 순서의 카드 목록이다. id만 두면 이어 받을 때 거리·제목을 다시 조회해야 해서
 * 캐시 히트가 DB 왕복을 없애지 못한다.
 *
 * 어댑터는 모든 실패를 삼켜 null/no-op으로 바꾼다. 실패는 기록만 한다.
 */
interface FeedBatchCachePort {
    /** 조건 해시 [key]의 [batchNo]번째 묶음. 미스·실패는 null. */
    fun get(key: String, batchNo: Int): List<SpotSearchResult>?

    fun put(key: String, batchNo: Int, cards: List<SpotSearchResult>)
}
