package org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.mapper

import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand
import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.dto.ClimateIndexItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 관광기후지수 응답 행 → 적재 명령(POL-033).
 *
 * 순수 함수다 — I/O도, 로깅도, 코드 유효성 검사도 하지 않는다. **기상청 코드를 우리 코드로
 * 바꾸지도 않는다**: 매핑은 데이터라 upsert SQL의 조인이 한다(V49).
 *
 * **필수 값이 없거나 파싱되지 않으면 null을 돌려준다** — 한 행의 결손이 페이지 전체를
 * 실패시키면 안 된다. 등급만 없어도 되는 값이라 null로 통과한다.
 */

/** `tm`은 `"yyyy-MM-dd HH:mm"`이며 날짜 부분만 쓴다. */
private val DATE_TIME_PREFIX_LENGTH = "uuuu-MM-dd".length

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")

/** 시군구 아이디 뒤에 붙는 `"00000"`을 뗀 실제 코드 길이. */
private const val DISTRICT_CODE_LENGTH = 5

fun ClimateIndexItem.toSyncCommandOrNull(): SyncClimateIndexCommand? {
    val code = cityAreaId.toDistrictCodeOrNull() ?: return null
    val date = tm.toForecastDateOrNull() ?: return null
    val value = kmaTci ?: return null
    return SyncClimateIndexCommand(
        districtCode = code,
        forecastDate = date,
        tciValue = value,
        tciGrade = tciGrade?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/**
 * `"5013000000"` → `"50130"`. 10자리 아이디의 뒤 5자리는 항상 `"00000"` 패딩이다.
 *
 * 5자리보다 짧으면 잘라낼 코드가 없다는 뜻이라 버린다 — 자르지 않고 통과시키면 자연키가
 * 다른 형태로 섞여 들어간다.
 */
private fun String?.toDistrictCodeOrNull(): String? =
    this?.trim()?.takeIf { it.length >= DISTRICT_CODE_LENGTH }?.substring(0, DISTRICT_CODE_LENGTH)

private fun String?.toForecastDateOrNull(): LocalDate? =
    this?.trim()?.takeIf { it.length >= DATE_TIME_PREFIX_LENGTH }?.let {
        runCatching { LocalDate.parse(it.substring(0, DATE_TIME_PREFIX_LENGTH), DATE_FORMAT) }.getOrNull()
    }
