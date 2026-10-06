package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.shared.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPage
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 월 지표 기준월 탐색(SPEC-0015 R6 · R8). 호출 횟수를 검증한다 — 시도마다 탐색하거나 찾은 뒤 재요청하면 곱해진다. */
class RegionMetricMonthResolverTest {

    private val runMonth = YearMonth.of(2026, 8)

    /** 호출 기록. (areaCd, 월, 페이지) 순서까지 봐야 탐색 방향을 알 수 있다. */
    private val calls = mutableListOf<Triple<String, YearMonth, Int>>()

    private fun row(code: String) = SyncDemandIntensityCommand(
        districtCode = code,
        baseMonth = LocalDate.of(2026, 6, 1),
        indicatorCode = "1",
        indicatorValue = BigDecimal.ONE,
    )

    /** [dataMonths]에만 데이터가 있는 업스트림. 그 외 월은 totalCount 0이다. */
    private fun resolver(
        lookbackMonths: Int = 6,
        dataMonths: Set<YearMonth>,
        emptyAreas: Set<String> = emptySet(),
    ) = RegionMetricMonthResolver(
        datasetName = "stayIntensity",
        lookbackMonths = lookbackMonths,
        fetch = { areaCd, month, pageNo ->
            calls += Triple(areaCd, month, pageNo)
            if (month in dataMonths && areaCd !in emptyAreas) {
                RegionMetricSyncPage(records = listOf(row("11110")), totalCount = 1, droppedCount = 0)
            } else {
                RegionMetricSyncPage(records = emptyList(), totalCount = 0, droppedCount = 0)
            }
        },
    )

    @Test
    fun `이번 달부터 거슬러 올라가 데이터가 있는 첫 달을 쓴다`() {
        // 최근 두 달은 0행이고 그 전 달에 데이터가 있는 상황.
        val context = ExecutionContext()
        val resolver = resolver(dataMonths = setOf(YearMonth.of(2026, 6)))

        val page = resolver.fetchPage(context, runMonth, "11", 1)

        assertEquals(1, page.totalCount)
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7), YearMonth.of(2026, 6)),
            calls.map { it.second },
        )
    }

    @Test
    fun `확정된 달은 ExecutionContext에 남는다`() {
        val context = ExecutionContext()
        resolver(dataMonths = setOf(YearMonth.of(2026, 6))).fetchPage(context, runMonth, "11", 1)

        assertEquals("2026-06", context.getString(RegionMetricMonthResolver.KEY_BASE_MONTH))
    }

    @Test
    fun `달을 찾은 시도는 탐색 응답을 그대로 쓰고 같은 요청을 다시 보내지 않는다`() {
        // 찾자마자 재요청하면 시도마다 호출이 한 건씩 늘어난다.
        val context = ExecutionContext()
        val resolver = resolver(dataMonths = setOf(YearMonth.of(2026, 8)))

        val page = resolver.fetchPage(context, runMonth, "11", 1)

        assertEquals(1, page.totalCount)
        assertEquals(listOf(Triple("11", YearMonth.of(2026, 8), 1)), calls)
    }

    @Test
    fun `두 번째 시도부터는 탐색 없이 확정된 달로 바로 부른다`() {
        // 시도마다 탐색하면 17 시도 × lookback만큼 호출이 곱해진다.
        val context = ExecutionContext()
        val resolver = resolver(dataMonths = setOf(YearMonth.of(2026, 6)))

        resolver.fetchPage(context, runMonth, "11", 1) // 8 → 7 → 6 (3 호출)
        calls.clear()
        resolver.fetchPage(context, runMonth, "26", 1)
        resolver.fetchPage(context, runMonth, "50", 1)

        assertEquals(
            listOf(
                Triple("26", YearMonth.of(2026, 6), 1),
                Triple("50", YearMonth.of(2026, 6), 1),
            ),
            calls,
        )
    }

    @Test
    fun `재시작해도 확정된 달을 다시 탐색하지 않는다`() {
        // ExecutionContext에서 복원된 상태. 새 인스턴스라도 재탐색이 없어야 한다.
        val context = ExecutionContext()
        context.putString(RegionMetricMonthResolver.KEY_BASE_MONTH, "2026-06")

        resolver(dataMonths = setOf(YearMonth.of(2026, 6))).fetchPage(context, runMonth, "11", 1)

        assertEquals(listOf(Triple("11", YearMonth.of(2026, 6), 1)), calls)
    }

    @Test
    fun `데이터가 없는 시도를 만나도 달을 확정하지 않고 다음 시도에서 다시 탐색한다`() {
        // 첫 시도가 비었다고 창 전체를 판정하면 안 된다.
        val context = ExecutionContext()
        val resolver = resolver(dataMonths = setOf(YearMonth.of(2026, 6)), emptyAreas = setOf("11"))

        val first = resolver.fetchPage(context, runMonth, "11", 1)

        assertEquals(0, first.totalCount)
        assertTrue(context.getString(RegionMetricMonthResolver.KEY_BASE_MONTH, "").isEmpty())

        calls.clear()
        val second = resolver.fetchPage(context, runMonth, "26", 1)

        assertEquals(1, second.totalCount)
        assertEquals(YearMonth.of(2026, 6), calls.last().second)
        assertEquals("2026-06", context.getString(RegionMetricMonthResolver.KEY_BASE_MONTH))
    }

    @Test
    fun `창 안에 데이터가 없으면 예외 없이 빈 페이지를 돌려준다`() {
        // R8 — 빈 응답은 정상이다. 0건 적재로 끝나야 한다.
        val context = ExecutionContext()
        val resolver = resolver(lookbackMonths = 3, dataMonths = emptySet())

        val page = resolver.fetchPage(context, runMonth, "11", 1)

        assertEquals(0, page.totalCount)
        assertEquals(emptyList(), page.records)
        assertEquals(0, page.droppedCount)
    }

    @Test
    fun `탐색은 lookback 개월을 넘지 않는다`() {
        val context = ExecutionContext()
        resolver(lookbackMonths = 3, dataMonths = emptySet()).fetchPage(context, runMonth, "11", 1)

        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7), YearMonth.of(2026, 6)),
            calls.map { it.second },
        )
    }

    @Test
    fun `달이 확정된 뒤에는 요청한 페이지 번호를 그대로 넘긴다`() {
        // 2페이지 이후가 1페이지로 되돌아가면 같은 페이지를 무한히 받는다.
        val context = ExecutionContext()
        context.putString(RegionMetricMonthResolver.KEY_BASE_MONTH, "2026-06")

        resolver(dataMonths = setOf(YearMonth.of(2026, 6))).fetchPage(context, runMonth, "11", 3)

        assertEquals(listOf(Triple("11", YearMonth.of(2026, 6), 3)), calls)
    }

    @Test
    fun `확정 전이면 탐색도 요청한 페이지 번호로 한다`() {
        // 재시작 직후 2페이지에서 이어지는 경우.
        val context = ExecutionContext()
        resolver(dataMonths = setOf(YearMonth.of(2026, 7))).fetchPage(context, runMonth, "11", 2)

        assertEquals(listOf(2, 2), calls.map { it.third })
        assertNull(calls.firstOrNull { it.third != 2 })
    }
}
