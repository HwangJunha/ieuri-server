package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 관광지 전량 동기화 아웃바운드 포트(SPEC-0004 R-2). 배치가 쓰는 수집 경로이며, 조회 경로인
 * [TourInfoPort]와 **의도적으로 분리한다** — 성격이 다르기 때문이다:
 *
 *  - [TourInfoPort.findNearby]는 좌표·반경 기준 라이브 조회(locationBasedList2)로, 요청당 한 페이지를 본다.
 *  - 이 포트는 좌표와 무관한 전량 수집(areaBasedSyncList2)으로, 언어당 전 페이지를 훑는다.
 *
 * 한 인터페이스에 합치면 조회 경로가 쓰지 않는 메서드를 떠안고, R-8 이후 `spots`가 TourAPI를 전혀 쓰지
 * 않게 되는 방향(조회 구현체 교체)과도 어긋난다.
 *
 * 반환 타입이 [SyncSpotCommand]인 것은 수집 결과가 곧 적재 입력이기 때문이다. 조회 경로의
 * [NearbySpotData]와 달리 거리·기준 좌표 개념이 없다.
 */
interface SpotSyncPort {
    /**
     * [language]의 [pageNo] 페이지를 수집한다(1-base). 대표이미지가 없거나 필수 필드가 빈 항목은
     * 어댑터가 드랍하므로, 반환된 레코드는 그대로 적재 가능하다(R-6).
     *
     * upstream 장애·타임아웃은 예외로 전파한다 — 배치가 해당 페이지만 실패로 기록하고 나머지를 커밋된
     * 상태로 남기기 위함이다(R-3·AC-7·AC-9).
     */
    fun fetchPage(language: DomainLocale, pageNo: Int, numOfRows: Int): SpotSyncPage
}

/**
 * 수집된 한 페이지. [records]는 필터를 통과해 적재 가능한 항목이고, [totalCount]는 upstream이 보고한
 * 필터-이전 총계다.
 *
 * [droppedCount]·[categoryFallbackCount]·[excludedCount]는 **관측용**이다(설계 문서 §2.3·§4.5). 정상
 * 실행에서 카테고리 폴백이 수천 건 발생하면 그것은 데이터가 아니라 `category_external_mapping` 행이 빠졌다는
 * 신호이며, 원본 contentTypeId를 함께 적재하는 것만으로는 "얼마나 많이 폴백했는가"를 실행 시점에 알 수 없다.
 *
 * **[droppedCount]와 [excludedCount]는 성격이 다르다** — 앞은 필수 필드가 비어 적재할 수 없는 항목,
 * 뒤는 비관광이라 **의도적으로 뺀** 항목이다(SPEC-0018). 섞으면 키워드 설정 오류가 데이터 결손에 묻힌다.
 */
data class SpotSyncPage(
    val records: List<SyncSpotCommand>,
    val totalCount: Int,
    val droppedCount: Int,
    val categoryFallbackCount: Int,
    /** 비관광 키워드에 걸려 적재하지 않은 항목 수. 급증하면 키워드가 넓어졌다는 신호다. */
    val excludedCount: Int = 0,
) {
    /** upstream에 다음 페이지가 남았는지. 수집 루프의 종료 조건. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean =
        pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
