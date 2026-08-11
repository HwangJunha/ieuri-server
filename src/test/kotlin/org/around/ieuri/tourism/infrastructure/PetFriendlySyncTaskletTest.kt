package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand
import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPage
import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPort
import org.around.ieuri.tourism.application.port.out.persistence.PetFriendlyStorePort
import org.around.ieuri.tourism.infrastructure.batch.PetFriendlySyncTasklet
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 반려동물 Step의 수집 루프와 관측값(SPEC-0015 · V48).
 *
 * `FestivalSyncTaskletTest`와 같은 방식이다 — Spring 컨텍스트를 띄우지 않고 StepExecution만 직접
 * 만든다. 검증 대상이 배선이 아니라 **몇 페이지를 언제까지 도는가**이기 때문이다.
 */
class PetFriendlySyncTaskletTest {

    private val syncPort = mockk<PetFriendlySyncPort>()
    private val storePort = mockk<PetFriendlyStorePort>(relaxed = true)

    private fun record(id: String) = SyncPetFriendlyCommand(
        contentId = id,
        accompanyType = "전구역 동반가능",
        accompanyScope = null,
        requiredMatters = null,
        etcInfo = null,
    )

    private fun page(records: List<SyncPetFriendlyCommand>, totalCount: Int, dropped: Int = 0) =
        PetFriendlySyncPage(records = records, totalCount = totalCount, droppedCount = dropped)

    private fun stepExecution(): StepExecution =
        StepExecution(
            "petFriendlySyncStep",
            JobExecution(1L, JobInstance(1L, "spotSyncJob"), JobParameters()),
        )

    private fun run(tasklet: PetFriendlySyncTasklet, stepExecution: StepExecution): RepeatStatus? =
        tasklet.execute(StepContribution(stepExecution), ChunkContext(StepContext(stepExecution)))

    private fun tasklet(pageSize: Int = 2) =
        PetFriendlySyncTasklet(syncPort = syncPort, storePort = storePort, pageSize = pageSize)

    @Test
    fun `남은 페이지가 있으면 CONTINUABLE로 다음 페이지 번호를 남긴다`() {
        every { syncPort.fetchPage(1, 2) } returns page(listOf(record("1"), record("2")), totalCount = 5)
        every { storePort.upsertAll(any()) } returns 2

        val execution = stepExecution()
        val status = run(tasklet(), execution)

        assertEquals(RepeatStatus.CONTINUABLE, status)
        assertEquals(2, execution.executionContext.getInt(PetFriendlySyncTasklet.KEY_NEXT_PAGE))
        assertEquals(2, execution.executionContext.getInt(PetFriendlySyncTasklet.KEY_WRITTEN))
    }

    @Test
    fun `마지막 페이지에서 FINISHED로 끝난다`() {
        every { syncPort.fetchPage(1, 2) } returns page(listOf(record("1"), record("2")), totalCount = 4)
        every { syncPort.fetchPage(2, 2) } returns page(listOf(record("3"), record("4")), totalCount = 4)
        every { storePort.upsertAll(any()) } returns 2

        val execution = stepExecution()
        val tasklet = tasklet()

        assertEquals(RepeatStatus.CONTINUABLE, run(tasklet, execution))
        assertEquals(RepeatStatus.FINISHED, run(tasklet, execution))
        // 누적 적재 수가 페이지를 가로질러 합산된다.
        assertEquals(4, execution.executionContext.getInt(PetFriendlySyncTasklet.KEY_WRITTEN))
    }

    @Test
    fun `빈 응답은 오류가 아니라 정상 종료다 - R8`() {
        every { syncPort.fetchPage(1, 2) } returns page(emptyList(), totalCount = 0)
        every { storePort.upsertAll(emptyList()) } returns 0

        val execution = stepExecution()

        assertEquals(RepeatStatus.FINISHED, run(tasklet(), execution))
        assertEquals(0, execution.executionContext.getInt(PetFriendlySyncTasklet.KEY_WRITTEN))
    }

    @Test
    fun `드랍 건수를 페이지에 걸쳐 누적한다 - 응답 스키마 변경 감지용`() {
        every { syncPort.fetchPage(1, 2) } returns page(listOf(record("1")), totalCount = 4, dropped = 1)
        every { syncPort.fetchPage(2, 2) } returns page(listOf(record("3")), totalCount = 4, dropped = 1)
        every { storePort.upsertAll(any()) } returns 1

        val execution = stepExecution()
        val tasklet = tasklet()
        run(tasklet, execution)
        run(tasklet, execution)

        assertEquals(2, execution.executionContext.getInt(PetFriendlySyncTasklet.KEY_DROPPED))
    }

    @Test
    fun `재실행하면 남겨 둔 페이지 번호에서 이어진다`() {
        every { syncPort.fetchPage(3, 2) } returns page(listOf(record("5")), totalCount = 6)
        every { storePort.upsertAll(any()) } returns 1

        val execution = stepExecution()
        execution.executionContext.putInt(PetFriendlySyncTasklet.KEY_NEXT_PAGE, 3)

        run(tasklet(), execution)

        // 1페이지부터 다시 읽지 않는다 — 앞 페이지는 이미 커밋되어 있다.
        verify(exactly = 0) { syncPort.fetchPage(1, any()) }
        verify(exactly = 1) { syncPort.fetchPage(3, 2) }
    }

    @Test
    fun `수집한 레코드를 그대로 적재 포트에 넘긴다`() {
        val records = listOf(record("126508"), record("126509"))
        every { syncPort.fetchPage(1, 2) } returns page(records, totalCount = 2)
        every { storePort.upsertAll(records) } returns 2

        run(tasklet(), stepExecution())

        verify(exactly = 1) { storePort.upsertAll(records) }
    }
}
