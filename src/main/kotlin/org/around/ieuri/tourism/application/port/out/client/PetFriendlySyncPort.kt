package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand

/**
 * 반려동물 동반 가능 관광지 수집 포트(SPEC-0015 · V48).
 *
 * **언어 인자가 없다.** `detailPetTour2`는 KorService2에만 존재하고 EN/JA/ZH 서비스에는
 * 오퍼레이션 자체가 없다(실측 2026-08-11: NO_OPENAPI_SERVICE_ERROR). 언어를 받는 시그니처는
 * 있지도 않은 선택지를 있는 것처럼 보이게 하므로 두지 않는다.
 */
interface PetFriendlySyncPort {

    /**
     * [pageNo] 페이지를 수집한다(1-base). 필수 필드가 빈 항목은 어댑터가 드랍하므로 반환된
     * 레코드는 그대로 적재 가능하다.
     *
     * upstream 장애·타임아웃은 예외로 전파한다 — 배치가 그 페이지만 실패로 기록하고 앞 페이지를
     * 커밋된 상태로 남기기 위함이다.
     */
    fun fetchPage(pageNo: Int, numOfRows: Int): PetFriendlySyncPage
}

/**
 * 수집된 한 페이지.
 *
 * @param records 필터를 통과해 적재 가능한 항목.
 * @param totalCount 업스트림이 보고한 필터-이전 총계.
 * @param droppedCount contentId 결손으로 버린 행 수. 0이 아니면 응답 스키마가 바뀌었다는 신호다.
 */
data class PetFriendlySyncPage(
    val records: List<SyncPetFriendlyCommand>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /**
     * 업스트림에 다음 페이지가 남았는지. 수집 루프의 종료 조건이다.
     *
     * **빈 응답(totalCount=0)이면 false다** — 빈 응답은 오류가 아니라 정상 종료다(R8).
     */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean =
        pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
