package org.around.ieuri.tourism.domain.spot

/**
 * 한 언어의 전량 수집 회차가 본 contentId. 이 집합에 없는 원본은 상류에서 사라진 것이라 지운다.
 * - 두 경우에는 집합을 믿을 수 없어 정리를 막는다. 막지 않으면 대량 오삭제다.
 *   1. 재개 실행 — 앞 페이지를 이번 회차가 보지 않았다.
 *   2. 0건 — 상류 일시 오류로 빈 목록을 받았을 수 있다.
 */
data class SpotHarvest(
    val contentIds: Set<String>,
    /** 1페이지가 아닌 곳에서 시작한 실행인가. */
    val resumed: Boolean,
) {
    /** 지우지 않고 남길 contentId. 정리를 막아야 하면 [SweepBlockedException]이다. */
    fun survivors(): Set<String> {
        if (resumed) throw SweepBlockedException("resumed run (incomplete id set)")
        if (contentIds.isEmpty()) throw SweepBlockedException("collected 0 items")
        return contentIds
    }
}
