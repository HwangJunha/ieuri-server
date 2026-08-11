package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand
import org.around.ieuri.tourism.application.port.out.client.ClimateIndexSyncPort
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.application.port.out.persistence.ClimateIndexStorePort
import org.around.ieuri.tourism.infrastructure.batch.ClimateIndexSyncTasklet
import org.around.ieuri.tourism.infrastructure.batch.RUN_DATE_PARAMETER
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 관광기후지수 Step의 페이지 루프와 관측값(SPEC-0015 · V49 · R7 · R8).
 *
 * Spring 컨텍스트를 띄우지 않고 StepExecution만 직접 만든다 — 검증 대상이 프레임워크 배선이
 * 아니라 **어떤 인자로 무엇을 호출하고 어디서 재개하는가**이기 때문이다.
 */
class ClimateIndexSyncTaskletTest {

    private val runDate = LocalDate.of(2026, 8, 11)
    private val syncPort = mockk<ClimateIndexSyncPort>()
    private val storePort = mockk<ClimateIndexStorePort>()

    private val pageSize = 100
    private val days = 3

    private fun tasklet() = ClimateIndexSyncTasklet(syncPort, storePort, days, pageSize)

    private fun record(code: String) = SyncClimateIndexCommand(
        districtCode = code,
        forecastDate = runDate.plusDays(1),
        tciValue = BigDecimal("0.06"),
        tciGrade = "보통",
    )

    private fun page(records: List<SyncClimateIndexCommand>, totalCount: Int, dropped: Int = 0) =
        TourStatsSyncPage(records = records, totalCount = totalCount, droppedCount = dropped)

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addString(RUN_DATE_PARAMETER, runDate.toString())
            .toJobParameters()
        return StepExecution(
            "districtClimateIndexSyncJobStep",
            JobExecution(1L, JobInstance(1L, "districtClimateIndexSyncJob"), parameters),
        )
    }

    private fun run(tasklet: ClimateIndexSyncTasklet, execution: StepExecution): RepeatStatus? =
        tasklet.execute(StepContribution(execution), ChunkContext(StepContext(execution)))

    /** 잡 파라미터의 실행일이 그대로 업스트림 기준일로 나간다 — Step 안에서 now()를 부르지 않는다. */
    @Test
    fun `조회 기준일과 일수는 잡 파라미터와 설정에서 온다`() {
        val baseDate = slot<LocalDate>()
        val daysSlot = slot<Int>()
        every {
            syncPort.fetchClimateIndices(capture(baseDate), capture(daysSlot), any(), any())
        } returns page(listOf(record("50130")), totalCount = 1)
        every { storePort.upsertClimateIndices(any()) } returns 1

        run(tasklet(), stepExecution())

        assertEquals(runDate, baseDate.captured)
        assertEquals(days, daysSlot.captured)
    }

    /** 페이지가 남으면 다음 페이지로 이어지고, 다 읽으면 끝난다. */
    @Test
    fun `다음 페이지가 남으면 이어서 읽는다`() {
        val execution = stepExecution()
        val tasklet = tasklet()
        every { syncPort.fetchClimateIndices(any(), any(), 1, pageSize) } returns
            page(List(pageSize) { record("5013$it") }, totalCount = 150)
        every { syncPort.fetchClimateIndices(any(), any(), 2, pageSize) } returns
            page(List(50) { record("1111$it") }, totalCount = 150)
        every { storePort.upsertClimateIndices(any()) } returnsMany listOf(pageSize, 50)

        assertEquals(RepeatStatus.CONTINUABLE, run(tasklet, execution))
        assertEquals(RepeatStatus.FINISHED, run(tasklet, execution))

        verify(exactly = 1) { syncPort.fetchClimateIndices(any(), any(), 1, pageSize) }
        verify(exactly = 1) { syncPort.fetchClimateIndices(any(), any(), 2, pageSize) }
        assertEquals(150, execution.executionContext.getInt(ClimateIndexSyncTasklet.KEY_WRITTEN))
    }

    /** R8 — 빈 응답은 장애가 아니다. 한 번에 끝나며 예외가 나지 않는다. */
    @Test
    fun `빈 응답은 정상 종료다`() {
        every { syncPort.fetchClimateIndices(any(), any(), any(), any()) } returns
            page(emptyList(), totalCount = 0)
        every { storePort.upsertClimateIndices(emptyList()) } returns 0

        val execution = stepExecution()
        assertEquals(RepeatStatus.FINISHED, run(tasklet(), execution))
        assertEquals(0, execution.executionContext.getInt(ClimateIndexSyncTasklet.KEY_WRITTEN))
    }

    /** 어댑터가 버린 행 수는 누적 관측된다 — 크면 우리 DTO 필드명이 틀렸다는 신호다. */
    @Test
    fun `드랍 건수는 페이지를 가로질러 누적된다`() {
        val execution = stepExecution()
        val tasklet = tasklet()
        every { syncPort.fetchClimateIndices(any(), any(), 1, pageSize) } returns
            page(List(pageSize) { record("5013$it") }, totalCount = 120, dropped = 3)
        every { syncPort.fetchClimateIndices(any(), any(), 2, pageSize) } returns
            page(List(20) { record("1111$it") }, totalCount = 120, dropped = 4)
        every { storePort.upsertClimateIndices(any()) } returnsMany listOf(pageSize, 20)

        run(tasklet, execution)
        run(tasklet, execution)

        assertEquals(7, execution.executionContext.getInt(ClimateIndexSyncTasklet.KEY_DROPPED))
    }

    /** 재실행은 중단 지점에서 이어진다 — 앞 페이지를 다시 읽지 않는다. */
    @Test
    fun `재개하면 저장된 페이지 번호부터 읽는다`() {
        val execution = stepExecution()
        execution.executionContext.putInt(ClimateIndexSyncTasklet.KEY_NEXT_PAGE, 3)
        every { syncPort.fetchClimateIndices(any(), any(), 3, pageSize) } returns
            page(listOf(record("50130")), totalCount = 201)
        every { storePort.upsertClimateIndices(any()) } returns 1

        run(tasklet(), execution)

        verify(exactly = 1) { syncPort.fetchClimateIndices(any(), any(), 3, pageSize) }
        verify(exactly = 0) { syncPort.fetchClimateIndices(any(), any(), 1, pageSize) }
    }

    /** 수집한 행은 그대로 적재로 넘어간다 — Step은 코드를 걸러내지 않는다(매핑은 SQL이 한다). */
    @Test
    fun `매핑되지 않는 코드도 걸러내지 않고 적재한다`() {
        val records = listOf(record("42210"), record("12210"), record("99999"))
        val captured = slot<List<SyncClimateIndexCommand>>()
        every { syncPort.fetchClimateIndices(any(), any(), any(), any()) } returns
            page(records, totalCount = 3)
        every { storePort.upsertClimateIndices(capture(captured)) } returns 3

        run(tasklet(), stepExecution())

        assertEquals(listOf("42210", "12210", "99999"), captured.captured.map { it.districtCode })
    }
}
