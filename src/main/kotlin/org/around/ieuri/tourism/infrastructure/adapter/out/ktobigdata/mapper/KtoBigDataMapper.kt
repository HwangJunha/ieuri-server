package org.around.ieuri.tourism.infrastructure.adapter.out.ktobigdata.mapper

import org.around.ieuri.tourism.application.command.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.command.SyncVisitorCountCommand
import org.around.ieuri.tourism.infrastructure.adapter.out.ktobigdata.dto.KtoConcentrationRateItem
import org.around.ieuri.tourism.infrastructure.adapter.out.ktobigdata.dto.KtoDemandIntensityItem
import org.around.ieuri.tourism.infrastructure.adapter.out.ktobigdata.dto.KtoVisitorCountItem
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * KTO 빅데이터 응답 행 → 적재 명령(POL-033).
 *
 * **세 가지 시군구 코드 표기가 여기서 하나가 된다** — `signguCode`(방문자수) ·
 * `signguCd`(나머지) → `sigunguCode`. 이 경계 밖으로 업스트림 이름이 나가지 않는다(설계 §8).
 *
 * 순수 함수다 — I/O도, 로깅도, 코드 유효성 검사도 하지 않는다. 알 수 없는 시군구 코드 필터링은
 * 호출자(배치)의 몫이다(R9).
 *
 * **필수 값이 없거나 파싱되지 않으면 null을 돌려준다.** 응답 스키마가 문서로 확정되지 않아
 * 한 행의 결손이 페이지 전체를 실패시키면 안 된다.
 */

/** `yyyyMMdd`. */
private val DATE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd")

/** `yyyyMM`. */
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuuMM")

fun KtoVisitorCountItem.toSyncCommandOrNull(): SyncVisitorCountCommand? {
    val code = signguCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val baseDate = baseYmd.toLocalDateOrNull() ?: return null
    // 구분 코드가 있으면 코드를, 없으면 명칭을 쓴다 — 컬럼은 varchar(32)이고 CHECK이 없어
    // KTO가 구분을 늘려도 적재가 막히지 않는다.
    val type = (touDivCd ?: touDivNm)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // **소수 문자열이다**("176473.5"). Int로 좁히면 값이 조용히 반올림된다(R4).
    val count = touNum.toBigDecimalOrNull() ?: return null

    return SyncVisitorCountCommand(
        sigunguCode = code,
        baseDate = baseDate,
        visitorType = type,
        touristCount = count,
    )
}

fun KtoConcentrationRateItem.toSyncCommandOrNull(): SyncConcentrationRateCommand? {
    val code = signguCd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val spotName = tAtsNm?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // 예측 대상일이다. 각 행의 날짜가 서로 다르며 그대로 보존해야 한다(R5).
    val targetDate = baseYmd.toLocalDateOrNull() ?: return null
    val rate = cnctrRate.toBigDecimalOrNull() ?: return null

    return SyncConcentrationRateCommand(
        sigunguCode = code,
        touristSpotName = spotName,
        targetDate = targetDate,
        concentrationRate = rate,
    )
}

/** 체류·소비 강도 공용. 응답 모양이 같아 적재 테이블만 호출자가 고른다. */
fun KtoDemandIntensityItem.toSyncCommandOrNull(): SyncDemandIntensityCommand? {
    val code = signguCd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val month = baseYm.toBaseMonthOrNull() ?: return null
    val indicator = cateCd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val value = dsCnt.toBigDecimalOrNull() ?: return null

    return SyncDemandIntensityCommand(
        sigunguCode = code,
        baseMonth = month,
        indicatorCode = indicator,
        indicatorValue = value,
    )
}

private fun String?.toLocalDateOrNull(): LocalDate? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { LocalDate.parse(it, DATE_FORMAT) }.getOrNull()
    }

/** `"202601"` → `2026-01-01`. 컬럼이 date라 해당 월 1일로 정규화한다(V44 주석). */
private fun String?.toBaseMonthOrNull(): LocalDate? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { java.time.YearMonth.parse(it, MONTH_FORMAT).atDay(1) }.getOrNull()
    }

private fun String?.toBigDecimalOrNull(): BigDecimal? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }
