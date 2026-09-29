package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * KTO 빅데이터 동기화 배치 설정(SPEC-0015 §2). 실제 값은 `application-batch.yaml`에 있다.
 *
 * @param monthlyLookbackMonths 월 지표에서 이번 달부터 거슬러 올라가며 데이터가 있는 달을 찾는 최대 개월.
 *   창 안에 데이터가 없으면 0건 적재로 정상 종료한다(R8).
 * @param visitorBackfillDays 매 실행이 다시 받아올 방문자수 구간의 길이(일). 실패한 날의 결손을 다음 실행이 메운다(R7).
 */
@ConfigurationProperties(prefix = "ieuri.tour-stats.sync")
data class TourStatsSyncProperties(
    val monthlyLookbackMonths: Int = 6,
    val visitorBackfillDays: Long = 90,
) {
    init {
        require(monthlyLookbackMonths >= 1) {
            "monthlyLookbackMonths는 1 이상이어야 합니다: $monthlyLookbackMonths"
        }
        require(visitorBackfillDays >= 1) { "visitorBackfillDays는 1 이상이어야 합니다: $visitorBackfillDays" }
    }
}
