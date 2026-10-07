package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.shared.provided.RegionMetricPageResult
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** KTO 지표 Tasklet의 순회·페이지 진행·재개(SPEC-0015 R4~R8). 적재와 미등록 코드 거름은 유스케이스가 한다. */
class RegionMetricSyncTaskletTest {

    /** (대상, 페이지) 호출 기록. */
    private val calls = mutableListOf<Pair<String, Int>>()

    private fun result(totalCount: Int, written: Int = 1, skipped: Int = 0, dropped: Int = 0) = RegionMetricPageResult(written = written, skippedUnknownCode = skipped, dropped = dropped, totalCount = totalCount)

    private fun tasklet(targets: List<String>, totals: Map<String, Int> = emptyMap(), runDates: MutableList<LocalDate> = mutableListOf()) = RegionMetricSyncTasklet(
        datasetName = "test",
        pageSize = PAGE_SIZE,
        targets = { targets },
        syncPage = { context, target, pageNo ->
            calls += target to pageNo
            runDates += context.runDate
            result(totalCount = totals[target] ?: 1)
        },
    )

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder().addString(RUN_DATE_PARAMETER, RUN_DATE.toString()).toJobParameters()
        return StepExecution(1L, "testStep", JobExecution(1L, JobInstance(1L, "testJob"), parameters))
    }

    private fun runToEnd(tasklet: RegionMetricSyncTasklet<String>, step: StepExecution = stepExecution()): Int {
        var runs = 0
        do {
            runs++
            val status = tasklet.execute(StepContribution(step), ChunkContext(StepContext(step)))
        } while (status == RepeatStatus.CONTINUABLE)
        return runs
    }

    @Test
    fun `한 대상에 페이지가 남으면 같은 대상의 다음 페이지를 먼저 부르고, 끝나면 다음 대상의 1페이지다`() {
        runToEnd(tasklet(listOf("11", "26"), totals = mapOf("11" to PAGE_SIZE + 1)))

        assertEquals(listOf("11" to 1, "11" to 2, "26" to 1), calls)
    }

    @Test
    fun `빈 응답이어도 다음 대상으로 넘어간다`() {
        runToEnd(tasklet(listOf("11", "26"), totals = mapOf("11" to 0)))

        assertEquals(listOf("11" to 1, "26" to 1), calls)
    }

    @Test
    fun `중단 지점의 대상 인덱스와 페이지 번호에서 재개한다`() {
        val step = stepExecution()
        step.executionContext.putInt(RegionMetricSyncTasklet.KEY_REGION_INDEX, 1)
        step.executionContext.putInt(RegionMetricSyncTasklet.KEY_NEXT_PAGE, 2)

        runToEnd(tasklet(listOf("11", "26"), totals = mapOf("26" to PAGE_SIZE + 1)), step)

        assertEquals(listOf("26" to 2), calls)
    }

    @Test
    fun `적재·미등록·드랍 건수를 누적하고 기준일은 잡 파라미터에서 온다`() {
        val runDates = mutableListOf<LocalDate>()
        val step = stepExecution()

        runToEnd(tasklet(listOf("11", "26"), runDates = runDates), step)

        assertEquals(2, step.executionContext.getInt(RegionMetricSyncTasklet.KEY_WRITTEN))
        assertEquals(listOf(RUN_DATE, RUN_DATE), runDates)
    }

    @Test
    fun `순회 대상이 비어 있으면 호출 없이 끝난다`() {
        assertEquals(1, runToEnd(tasklet(emptyList())))
        assertEquals(emptyList(), calls)
    }

    private companion object {
        const val PAGE_SIZE = 100
        val RUN_DATE: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
