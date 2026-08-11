package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * KTO 빅데이터 동기화 배치 설정(SPEC-0015 §2). 실제 값은 `application-batch.yaml`에 있으며
 * batch 프로파일로 뜬 프로세스만 읽는다.
 *
 * @param monthlyLookbackMonths 월 지표에서 이번 달부터 거슬러 올라가며 데이터가 있는 달을
 *   찾는 최대 개월. **고정 소급월(`monthly-lag-months`)을 대체한다** — 가용한 최신 월이
 *   고정되어 있지 않기 때문이다(2026-08-11 실측: 202608·202607은 0행, 202606에 데이터).
 *   창 안에 데이터가 하나도 없으면 0건 적재로 정상 종료한다(R8).
 * @param visitorBackfillDays 매 실행이 다시 받아올 방문자수 구간의 길이(일). **하루치만 받으면
 *   배치가 실패한 날의 데이터가 영구히 빈다** — 다음 실행은 그 다음날치를 가져갈 뿐이라 아무도
 *   모르게 결손이 남는다. 자연키 upsert라(R7) 재수집해도 행은 늘지 않고 바뀐 값만 갱신된다.
 *   API가 날짜 범위를 지원해 30일치가 호출 1건이다(1일 = 시군구 740행, 페이지 1,000).
 *
 * 월 지표는 소급 구간을 두지 않는다 — 찾아낸 최신 월 하나만 받는다. 결손이 나도 다음 실행이
 * 같은 달을 다시 받아 스스로 복구된다.
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
