package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * KTO 빅데이터 동기화 배치 설정(SPEC-0015 §2). 실제 값은 `application-batch.yaml`에 있으며
 * batch 프로파일로 뜬 프로세스만 읽는다.
 *
 * @param monthlyLagMonths 월 단위 지표의 소급 개월. 매월 16일에 발표되므로(§4.6) 이번 달을
 *   요청하면 대부분 빈 응답이다. 월 지표는 구간이 아니라 월 하나만 요청하므로 이 소급이
 *   필요하다 — 방문자수는 구간으로 받아 소급 없이 오늘까지 요청한다.
 * @param visitorBackfillDays 매 실행이 다시 받아올 방문자수 구간의 길이(일). **하루치만 받으면
 *   배치가 실패한 날의 데이터가 영구히 빈다** — 다음 실행은 그 다음날치를 가져갈 뿐이라 아무도
 *   모르게 결손이 남는다. 자연키 upsert라(R7) 재수집해도 행은 늘지 않고 바뀐 값만 갱신된다.
 *   API가 날짜 범위를 지원해 30일치가 호출 1건이다(1일 = 시군구 740행, 페이지 1,000).
 *
 * 월 지표는 소급 구간을 두지 않는다 — 오퍼레이션이 월 하나만 받아 구간 재수집이 호출 루프가
 * 되는데, 매월 16일에 한 번 갱신되는 값이라(§4.6) 매일 도는 배치가 같은 달을 계속 덮어쓴다.
 * 결손이 나도 다음 실행이 같은 달을 다시 받아 스스로 복구된다.
 */
@ConfigurationProperties(prefix = "ieuri.tour-stats.sync")
data class TourStatsSyncProperties(
    val monthlyLagMonths: Long = 1,
    val visitorBackfillDays: Long = 90,
) {
    init {
        require(visitorBackfillDays >= 1) { "visitorBackfillDays는 1 이상이어야 합니다: $visitorBackfillDays" }
    }
}
