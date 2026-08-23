package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.LocalDate

/**
 * 축제 전용 정보 수집 아웃바운드 포트(SPEC-0007 R-3). [SpotSyncPort]와 **대칭으로 나란히 두고,
 * 기존 포트에 메서드를 추가하지 않는다** — 오퍼레이션이 다르고(`searchFestival2` vs
 * `areaBasedSyncList2`) 페이지네이션 성격도 다르다. [SpotSyncPort]가 [TourInfoPort]와 분리된 것과
 * 같은 이유가 그대로 적용된다.
 */
interface FestivalSyncPort {
    /**
     * [language]의 [pageNo] 페이지를 수집한다(1-base).
     *
     * **[eventStartDate]를 인자로 받는다.** `searchFestival2`의 필수 파라미터이며(AC-8), 이 값은
     * "그 날짜에 시작하는 행사"가 아니라 **"그 날짜 기준 유효한 행사"** 를 뜻한다. 어댑터 안에서
     * `LocalDate.now()`를 부르지 않고 밖에서 내려보내는 이유는 두 가지다 — 배치의 실행일 기준이
     * 한 곳(잡 파라미터)에서만 정해져야 하고, 그래야 날짜 의존 매핑을 단위 테스트로 고정할 수 있다.
     *
     * upstream 장애·타임아웃은 예외로 전파한다 — 한 언어의 실패가 다른 언어의 적재를 되돌리지
     * 않도록 Step 단위로 실패를 가둔다(R-6·AC-21).
     */
    fun fetchPage(
        language: DomainLocale,
        eventStartDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): FestivalSyncPage
}

/**
 * 수집된 한 페이지. [SpotSyncPage]와 같은 모양이되 `categoryFallbackCount`가 없다 —
 * `searchFestival2`는 축제만 돌려주므로 카테고리 해석 자체가 필요 없다.
 *
 * [droppedCount]는 **관측용이며 [TBD-2]의 해소 수단이다**(설계 문서 §6). 날짜 해석 실패가 유의미한
 * 규모로 발생하면 R-4의 "드랍" 정책을 재검토해야 하는데, 적재 건수와 구분해 남기지 않으면
 * 판단할 근거가 없다(AC-41).
 */
data class FestivalSyncPage(
    val records: List<SyncFestivalCommand>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /** upstream에 다음 페이지가 남았는지. 수집 루프의 종료 조건. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean =
        pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
