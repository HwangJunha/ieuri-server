package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.dto.ClimateIndexItem
import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.mapper.toSyncCommandOrNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 관광기후지수 응답 행 → 적재 명령 매핑(SPEC-0015 · V49 · POL-033).
 *
 * 필드마다 값을 다르게 둔다 — 통째 비교는 어느 필드가 뒤바뀌었는지 알려주지 못한다.
 */
class ClimateIndexMapperTest {

    @Test
    fun `모든 필드가 각각의 자리로 매핑된다`() {
        val item = ClimateIndexItem(
            tm = "2026-08-12 00:00",
            cityAreaId = "5013000000",
            totalCityName = "제주 서귀포시",
            doName = "제주",
            cityName = "서귀포시",
            kmaTci = BigDecimal("0.06"),
            tciGrade = "보통",
        )

        val command = item.toSyncCommandOrNull()!!

        assertEquals("50130", command.districtCode)
        assertEquals(LocalDate.of(2026, 8, 12), command.forecastDate)
        assertEquals(BigDecimal("0.06"), command.tciValue)
        assertEquals("보통", command.tciGrade)
    }

    /**
     * `cityAreaId`는 10자리(행정표준코드 5자리 + `"00000"`)라 앞 5자리만 쓴다. 자르지 않으면
     * 자연키가 다른 형태로 섞여 들어가 매핑 조인이 전부 빗나간다.
     */
    @ParameterizedTest
    @CsvSource(
        "5013000000, 50130",
        "1111000000, 11110",
        "4221000000, 42210",
        "1221000000, 12210",
        // 이미 5자리인 값은 그대로 남는다.
        "29110, 29110",
    )
    fun `시군구 아이디는 앞 5자리로 잘린다`(cityAreaId: String, expected: String) {
        val command = ClimateIndexItem(
            tm = "2026-08-12 00:00",
            cityAreaId = cityAreaId,
            kmaTci = BigDecimal("0.10"),
        ).toSyncCommandOrNull()

        assertEquals(expected, command?.districtCode)
    }

    @Test
    fun `등급이 없어도 매핑된다 - 나머지 필드는 그대로다`() {
        val command = ClimateIndexItem(
            tm = "2026-08-13 12:00",
            cityAreaId = "1111000000",
            kmaTci = BigDecimal("-0.66"),
            tciGrade = null,
        ).toSyncCommandOrNull()!!

        assertEquals("11110", command.districtCode)
        assertEquals(LocalDate.of(2026, 8, 13), command.forecastDate)
        assertEquals(BigDecimal("-0.66"), command.tciValue)
        assertNull(command.tciGrade)
    }

    /** 빈 문자열 등급은 null로 정규화한다 — `""`와 "등급 없음"이 두 값으로 갈리면 안 된다. */
    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `빈 등급 문자열은 null이 된다`(grade: String) {
        val command = ClimateIndexItem(
            tm = "2026-08-12 00:00",
            cityAreaId = "5013000000",
            kmaTci = BigDecimal("0.01"),
            tciGrade = grade,
        ).toSyncCommandOrNull()!!

        assertNull(command.tciGrade)
    }

    @Test
    fun `모든 필드가 null이면 예외 없이 null을 돌려준다`() {
        assertNull(ClimateIndexItem().toSyncCommandOrNull())
    }

    /** 필수 값이 없거나 형식이 깨진 행은 드랍한다 — 페이지 전체를 실패시키지 않는다. */
    @Test
    fun `필수 값이 없는 행은 드랍된다`() {
        val base = ClimateIndexItem(
            tm = "2026-08-12 00:00",
            cityAreaId = "5013000000",
            kmaTci = BigDecimal("0.06"),
        )

        assertNull(base.copy(cityAreaId = null).toSyncCommandOrNull())
        assertNull(base.copy(tm = null).toSyncCommandOrNull())
        assertNull(base.copy(kmaTci = null).toSyncCommandOrNull())
        // 5자리에 못 미치는 코드는 잘라낼 것이 없다.
        assertNull(base.copy(cityAreaId = "5013").toSyncCommandOrNull())
        // 날짜 형식이 깨진 행.
        assertNull(base.copy(tm = "2026/08/12 00:00").toSyncCommandOrNull())
        assertNull(base.copy(tm = "2026-13-45 00:00").toSyncCommandOrNull())
    }

    /** 0은 유효한 지수값이다 — falsy로 취급해 드랍하면 안 된다. */
    @Test
    fun `지수 0은 유효한 값이다`() {
        val command = ClimateIndexItem(
            tm = "2026-08-12 00:00",
            cityAreaId = "5013000000",
            kmaTci = BigDecimal.ZERO,
        ).toSyncCommandOrNull()!!

        assertEquals(BigDecimal.ZERO, command.tciValue)
    }
}
