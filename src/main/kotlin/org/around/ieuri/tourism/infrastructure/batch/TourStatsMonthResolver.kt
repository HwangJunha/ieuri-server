package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.time.YearMonth

/**
 * 월 지표(체류·소비 강도)에서 **실제로 데이터가 있는 최신 월**로 한 페이지를 가져온다.
 *
 * 고정 소급월을 쓰지 않는 이유: 가용한 최신 월이 고정되어 있지 않다(2026-08-11 실측 —
 * 202608·202607은 0행, 202606에 데이터). 지연 폭은 데이터셋마다 다를 수 있다.
 *
 * **월을 실행당 한 번만 정한다.** 시도마다 다시 탐색하면 17개 시도 × lookback만큼 호출이
 * 곱해진다. 확정 후에는 탐색 없이 그 달로 바로 부르며, 확정값이 ExecutionContext에 남아
 * 재시작 후에도 재탐색하지 않는다.
 *
 * **한 시도가 비었다고 포기하지 않는다** — 그 시도가 정말로 데이터가 없을 수 있으므로 월을
 * 확정하지 않고 다음 시도에서 다시 탐색한다. 창 안에 아무 데이터도 없으면 빈 페이지를 돌려
 * 0건 적재로 정상 종료한다(R8).
 */
class TourStatsMonthResolver(
    private val datasetName: String,
    private val lookbackMonths: Int,
    private val fetch: (areaCd: String, month: YearMonth, pageNo: Int) -> TourStatsSyncPage<SyncDemandIntensityCommand>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [areaCd]의 한 페이지. 기준월이 아직 확정되지 않았다면 이번 달부터 거슬러 올라가며 첫
     * 번째로 행이 오는 달을 찾고, **그 탐색 응답을 그대로 결과로 쓴다** — 찾자마자 같은 요청을
     * 다시 보내지 않기 위해서다.
     */
    fun fetchPage(
        executionContext: ExecutionContext,
        runMonth: YearMonth,
        areaCd: String,
        pageNo: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand> {
        resolvedMonth(executionContext)?.let { return fetch(areaCd, it, pageNo) }

        for (offset in 0 until lookbackMonths) {
            val candidate = runMonth.minusMonths(offset.toLong())
            val page = fetch(areaCd, candidate, pageNo)
            if (page.totalCount > 0) {
                executionContext.putString(KEY_BASE_MONTH, candidate.toString())
                log.info(
                    "kto sync month resolved: dataset={} baseMonth={} probedArea={} probes={}",
                    datasetName, candidate, areaCd, offset + 1,
                )
                return page
            }
        }

        log.info(
            "kto sync month unresolved: dataset={} area={} lookback={}개월 — 다음 시도에서 재탐색합니다.",
            datasetName, areaCd, lookbackMonths,
        )
        return EMPTY_PAGE
    }

    private fun resolvedMonth(executionContext: ExecutionContext): YearMonth? =
        executionContext.getString(KEY_BASE_MONTH, "")
            .takeIf { it.isNotEmpty() }
            ?.let { YearMonth.parse(it) }

    companion object {
        /** 확정된 기준월(`uuuu-MM`). 재시작해도 재탐색하지 않도록 ExecutionContext에 남긴다. */
        const val KEY_BASE_MONTH = "baseMonth"

        private val EMPTY_PAGE =
            TourStatsSyncPage<SyncDemandIntensityCommand>(records = emptyList(), totalCount = 0, droppedCount = 0)
    }
}
