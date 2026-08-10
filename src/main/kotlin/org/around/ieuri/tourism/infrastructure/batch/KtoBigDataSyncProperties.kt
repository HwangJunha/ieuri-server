package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * KTO 빅데이터 동기화 배치 설정(SPEC-0015 §2). 실제 값은 `application-batch.yaml`에 있으며
 * batch 프로파일로 뜬 프로세스만 읽는다.
 *
 * @param visitorLagDays 방문자수 조회의 소급 일수. **이동통신 원천이 4일 지연 공개된다**
 *   (spec §4.6) — 오늘 날짜로 부르면 빈 응답이 온다. 이 값은 데이터랩 포털의 갱신 주기 표에서
 *   확인한 관측 사실이며 API 매뉴얼에 없는 값이라 설정으로 둔다.
 * @param monthlyLagMonths 월 단위 지표의 소급 개월. 매월 16일에 발표되므로(§4.6) 이번 달을
 *   요청하면 대부분 빈 응답이다.
 */
@ConfigurationProperties(prefix = "ieuri.kto-bigdata.sync")
data class KtoBigDataSyncProperties(
    val visitorLagDays: Long = 4,
    val monthlyLagMonths: Long = 1,
)
