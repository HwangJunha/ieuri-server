package org.around.ieuri.tourism.adapter.client.kto.mapper

import org.around.ieuri.tourism.adapter.client.kto.dto.KtoConcentrationRateItem
import org.around.ieuri.tourism.adapter.client.kto.dto.KtoDemandIntensityItem
import org.around.ieuri.tourism.adapter.client.kto.dto.KtoVisitorCountItem
import org.around.ieuri.tourism.application.shared.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.provided.dto.SyncVisitorCountCommand
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * KTO 빅데이터 응답 행 → 적재 명령(POL-033). 시군구 코드 표기(`signguCode`·`signguCd`)가 `districtCode` 하나가 된다.
 * - 알 수 없는 시군구 코드 필터링은 호출자(배치)의 몫이다(R9).
 * - 필수 값이 없거나 파싱되지 않으면 null이다.
 */

/** `yyyyMMdd`. */
private val DATE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd")

/** `yyyyMM`. */
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuuMM")

fun KtoVisitorCountItem.toSyncCommandOrNull(): SyncVisitorCountCommand? {
    val code = signguCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val baseDate = baseYmd.toLocalDateOrNull() ?: return null
    // 구분 코드가 있으면 코드를, 없으면 명칭을 쓴다.
    val type = (touDivCd ?: touDivNm)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // 소수 문자열이다("176473.5"). Int로 좁히면 조용히 반올림된다(R4).
    val count = touNum.toBigDecimalOrNull() ?: return null

    return SyncVisitorCountCommand(
        districtCode = code,
        baseDate = baseDate,
        visitorType = type,
        touristCount = count,
    )
}

fun KtoConcentrationRateItem.toSyncCommandOrNull(): SyncConcentrationRateCommand? {
    val code = signguCd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val spotName = tAtsNm?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // 예측 대상일이다. 행마다 다르며 그대로 보존한다(R5).
    val targetDate = baseYmd.toLocalDateOrNull() ?: return null
    val rate = cnctrRate.toBigDecimalOrNull() ?: return null

    return SyncConcentrationRateCommand(
        districtCode = code,
        touristSpotName = spotName,
        targetDate = targetDate,
        concentrationRate = rate,
    )
}

/** 체류·소비 강도 공용. 적재 테이블은 호출자가 고른다. */
fun KtoDemandIntensityItem.toSyncCommandOrNull(): SyncDemandIntensityCommand? {
    val code = signguCd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val month = baseYm.toBaseMonthOrNull() ?: return null
    val indicator = indicatorCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val value = indicatorValue.toBigDecimalOrNull() ?: return null

    return SyncDemandIntensityCommand(
        districtCode = code,
        baseMonth = month,
        indicatorCode = indicator,
        indicatorValue = value,
    )
}

private fun String?.toLocalDateOrNull(): LocalDate? = this?.trim()?.takeIf { it.isNotEmpty() }?.let {
    runCatching { LocalDate.parse(it, DATE_FORMAT) }.getOrNull()
}

/** `"202601"` → `2026-01-01`. 컬럼이 date라 해당 월 1일로 정규화한다. */
private fun String?.toBaseMonthOrNull(): LocalDate? = this?.trim()?.takeIf { it.isNotEmpty() }?.let {
    runCatching { java.time.YearMonth.parse(it, MONTH_FORMAT).atDay(1) }.getOrNull()
}

private fun String?.toBigDecimalOrNull(): BigDecimal? = this?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }
