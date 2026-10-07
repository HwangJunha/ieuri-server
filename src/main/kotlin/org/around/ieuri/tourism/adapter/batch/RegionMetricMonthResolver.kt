package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.shared.provided.RegionMetricPageResult
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.time.YearMonth

/**
 * 월 지표(체류·소비 강도)에서 실제로 데이터가 있는 최신 월로 한 페이지를 동기화한다.
 * - 탐색은 동기화 호출 자체다 — 행이 없는 달은 적재할 것도 없다.
 * - 월은 실행당 한 번만 정하고 ExecutionContext에 남긴다 — 재시작해도 재탐색하지 않는다.
 * - 한 시도가 비었으면 월을 확정하지 않고 다음 시도에서 다시 탐색한다.
 * - 창 안에 데이터가 없으면 빈 페이지를 돌려 0건으로 정상 종료한다(R8).
 */
class RegionMetricMonthResolver(
    private val datasetName: String,
    private val lookbackMonths: Int,
    private val sync: (areaCd: String, month: YearMonth, pageNo: Int) -> RegionMetricPageResult,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** [areaCd]의 한 페이지. 미확정이면 이번 달부터 거슬러 올라가 행이 오는 첫 달을 찾고, 그 응답을 그대로 쓴다. */
    fun syncPage(
        executionContext: ExecutionContext,
        runMonth: YearMonth,
        areaCd: String,
        pageNo: Int,
    ): RegionMetricPageResult {
        resolvedMonth(executionContext)?.let { return sync(areaCd, it, pageNo) }

        for (offset in 0 until lookbackMonths) {
            val candidate = runMonth.minusMonths(offset.toLong())
            val page = sync(areaCd, candidate, pageNo)
            if (page.totalCount > 0) {
                executionContext.putString(KEY_BASE_MONTH, candidate.toString())
                log.info(
                    "kto sync month resolved: dataset={} baseMonth={} probedArea={} probes={}",
                    datasetName,
                    candidate,
                    areaCd,
                    offset + 1,
                )
                return page
            }
        }

        log.info(
            "kto sync month unresolved: dataset={} area={} lookback={}개월 — 다음 시도에서 재탐색합니다.",
            datasetName,
            areaCd,
            lookbackMonths,
        )
        return EMPTY_PAGE
    }

    private fun resolvedMonth(executionContext: ExecutionContext): YearMonth? = executionContext.getString(KEY_BASE_MONTH, "")
        .takeIf { it.isNotEmpty() }
        ?.let { YearMonth.parse(it) }

    companion object {
        /** 확정된 기준월(`uuuu-MM`). 재시작해도 재탐색하지 않도록 ExecutionContext에 남긴다. */
        const val KEY_BASE_MONTH = "baseMonth"

        private val EMPTY_PAGE = RegionMetricPageResult(written = 0, skippedUnknownCode = 0, dropped = 0, totalCount = 0)
    }
}
