package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.application.spot.required.dto.SpotSyncPage
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 관광지 전량 동기화 포트(SPEC-0004). 배치가 좌표와 무관하게 언어당 전 페이지를 수집한다. */
interface SpotSyncPort {
    /**
     * [language]의 [pageNo] 페이지(1부터)를 수집한다. 반환된 레코드는 그대로 적재 가능하다.
     * - upstream 장애·타임아웃은 예외로 전파한다.
     */
    fun fetchPage(language: DomainLocale, pageNo: Int, numOfRows: Int): SpotSyncPage
}
