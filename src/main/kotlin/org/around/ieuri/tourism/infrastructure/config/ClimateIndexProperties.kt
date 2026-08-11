package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 기상청 관광기후지수 접근 설정(SPEC-0015 · V49).
 *
 * **[TourApiProperties]와 같은 data.go.kr 계정 키를 쓴다**(실측 2026-08-11) — 그래도 블록을
 * 나눈 것은 서비스 제공처가 달라 별도 키로 갈릴 수 있어서다. yaml에서 TourAPI 키를 기본값으로
 * 물려받는다.
 *
 * 타임아웃 설정이 없는 것은 [org.around.ieuri.tourism.infrastructure.config.TourismConfig]의
 * `tourApiRestClient`를 그대로 쓰기 때문이다 — 여기 값을 두면 실제로 적용되지 않는 설정이 된다.
 *
 * @param days 조회 범위. `CURRENT_DATE`부터 이 일수까지의 자료가 온다(업스트림 `DAY` 파라미터).
 * @param pageSize 페이지당 행 수. 전국 1일치가 472행이라(실측) 1000이면 대부분 1페이지다.
 */
@ConfigurationProperties(prefix = "ieuri.climate-index")
data class ClimateIndexProperties(
    val serviceKey: String = "",
    val baseUrl: String = "https://apis.data.go.kr/1360000/TourStnInfoService1",
    val days: Int = 1,
    val pageSize: Int = 1_000,
)
