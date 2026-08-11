package org.around.ieuri.tourism.application.command

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 시군구 관광기후지수 적재 입력(SPEC-0015 · V49). 자연키는 (시군구 코드, 예보 대상일)이다.
 *
 * [districtCode]는 **기상청 코드를 그대로 담는다** — 우리 행정표준코드로의 변환은 upsert SQL의
 * 매핑 조인이 한다(V49의 두 매핑 테이블). 매핑을 데이터로 두면 행정구역이 다시 바뀌어도 행만
 * 더하면 되고, 코드에 담으면 재배포가 필요하다.
 *
 * [tciValue]는 기상청이 이미 판정한 지수 원값이다(실측 범위 -0.66 ~ 0.52). 정수가 아니라
 * [BigDecimal]이다. [tciGrade]는 등급 문자열이며 응답에 없을 수 있어 nullable이다.
 */
data class SyncClimateIndexCommand(
    val districtCode: String,
    val forecastDate: LocalDate,
    val tciValue: BigDecimal,
    val tciGrade: String?,
)
