package org.around.ieuri.tourism.adapter.batch

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.tourism.application.region.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricPageResult
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricTarget
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/** KTO 지표 Tasklet의 순회·페이지 진행·재개(SPEC-0015 R4~R8). 적재와 미등록 코드 거름은 유스케이스가 한다. */
class RegionMetricSyncTaskletTest {

    private val useCase = mockk<RegionMetricSyncUseCase>()

    /** (대상 시도 코드, 페이지, 넘겨받은 기준월) 호출 기록. */
    private val calls = mutableListOf<Triple<String?, Int, YearMonth?>>()

    private fun target(provinceCode: String) = RegionMetricTarget(provinceCode = provinceCode, districtCode = null)

    /**
     * [pagesByTarget]만큼 페이지가 있는 것처럼 응답한다. 기준월을 넘겨받지 않으면 [probedMonth]를 정해 돌려준다.
     */
    private fun tasklet(
        targets: List<RegionMetricTarget>,
        pagesByTarget: Map<String, Int> = emptyMap(),
        probedMonth: YearMonth? = null,
    ): RegionMetricSyncTasklet {
        every { useCase.targets(DATASET) } returns targets
        every { useCase.syncPage(DATASET, any(), any(), any(), any()) } answers {
            val target = secondArg<RegionMetricTarget>()
            val pageNo = thirdArg<Int>()
            val baseMonth = arg<YearMonth?>(4)
            assertEquals(RUN_DATE, arg<LocalDate>(3))
            calls += Triple(target.provinceCode, pageNo, baseMonth)
            RegionMetricPageResult(
                written = 2,
                skippedUnknownCode = 3,
                dropped = 5,
                hasMore = pageNo < (pagesByTarget[target.provinceCode] ?: 1),
                baseMonth = baseMonth ?: probedMonth,
            )
        }
        return RegionMetricSyncTasklet(DATASET, useCase)
    }

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder().addString(RUN_DATE_PARAMETER, RUN_DATE.toString()).toJobParameters()
        return StepExecution(1L, "testStep", JobExecution(1L, JobInstance(1L, "testJob"), parameters))
    }

    private fun runToEnd(tasklet: RegionMetricSyncTasklet, step: StepExecution = stepExecution()): Int {
        var runs = 0
        do {
            runs++
            val status = tasklet.execute(StepContribution(step), ChunkContext(StepContext(step)))
        } while (status == RepeatStatus.CONTINUABLE)
        return runs
    }

    @Test
    fun `한 대상에 페이지가 남으면 같은 대상의 다음 페이지를 먼저 부르고, 끝나면 다음 대상의 1페이지다`() {
        runToEnd(tasklet(listOf(target("11"), target("26")), pagesByTarget = mapOf("11" to 2)))

        assertEquals(listOf("11" to 1, "11" to 2, "26" to 1), calls.map { it.first to it.second })
    }

    @Test
    fun `중단 지점의 대상 인덱스와 페이지 번호에서 재개한다`() {
        val step = stepExecution()
        step.executionContext.putInt(RegionMetricSyncTasklet.KEY_TARGET_INDEX, 1)
        step.executionContext.putInt(RegionMetricSyncTasklet.KEY_NEXT_PAGE, 2)

        runToEnd(tasklet(listOf(target("11"), target("26")), pagesByTarget = mapOf("26" to 2)), step)

        assertEquals(listOf("26" to 2), calls.map { it.first to it.second })
    }

    @Test
    fun `첫 페이지가 정한 기준월을 이후 페이지와 재개에 넘긴다`() {
        val step = stepExecution()

        runToEnd(tasklet(listOf(target("11"), target("26")), probedMonth = YearMonth.of(2026, 6)), step)

        assertEquals(listOf(null, YearMonth.of(2026, 6)), calls.map { it.third })
        assertEquals("2026-06", step.executionContext.getString(RegionMetricSyncTasklet.KEY_BASE_MONTH))
    }

    @Test
    fun `적재·미등록·드랍 건수를 각각 누적한다`() {
        val step = stepExecution()

        runToEnd(tasklet(listOf(target("11"), target("26"))), step)

        assertEquals(4, step.executionContext.getInt(RegionMetricSyncTasklet.KEY_WRITTEN))
        assertEquals(6, step.executionContext.getInt(RegionMetricSyncTasklet.KEY_SKIPPED_UNKNOWN_CODE))
        assertEquals(10, step.executionContext.getInt(RegionMetricSyncTasklet.KEY_DROPPED))
    }

    @Test
    fun `순회 대상이 비어 있으면 호출 없이 끝난다`() {
        assertEquals(1, runToEnd(tasklet(emptyList())))
        assertEquals(emptyList(), calls)
    }

    private companion object {
        val DATASET = RegionMetricDataset.STAY_INTENSITY
        val RUN_DATE: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
