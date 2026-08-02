package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPage
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncTasklet
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 동기화 Tasklet의 페이지 진행·재개 동작(SPEC-0004 R-3·R-4).
 *
 * Spring 컨텍스트를 띄우지 않고 StepExecution만 직접 만들어 검증한다 — 검증 대상이 프레임워크 배선이
 * 아니라 **페이지 루프와 ExecutionContext에 남는 재개 상태**이기 때문이다.
 */
class SpotSyncTaskletTest {

    private val syncPort = mockk<SpotSyncPort>()
    private val storePort = mockk<SpotStorePort>(relaxed = true)

    private fun record(id: String) = SyncSpotCommand(
        contentId = id,
        language = DomainLocale.KO,
        title = "경복궁",
        address = "서울",
        thumbnailImageUrl = "https://img/$id.jpg",
        coordinates = Coordinates(latitude = 37.56, longitude = 126.98),
        category = SpotCategory.TOURIST_SPOT,
        contentTypeId = 12,
        copyright = CopyrightType.UNSPECIFIED,
    )

    private fun page(records: List<SyncSpotCommand>, totalCount: Int) =
        SpotSyncPage(records = records, totalCount = totalCount, droppedCount = 0, categoryFallbackCount = 0)

    /** 실제 StepExecution — ExecutionContext가 진짜로 유지되어야 재개 동작을 검증할 수 있다. */
    private fun stepExecution(): StepExecution =
        StepExecution("syncLanguageStep-KO", JobExecution(1L, JobInstance(1L, "spotSyncJob"), JobParameters()))

    private fun run(tasklet: SpotSyncTasklet, stepExecution: StepExecution): RepeatStatus? {
        val contribution = StepContribution(stepExecution)
        val chunkContext = ChunkContext(StepContext(stepExecution))
        return tasklet.execute(contribution, chunkContext)
    }

    private fun tasklet(pageSize: Int = 1_000) = SpotSyncTasklet(
        language = DomainLocale.KO,
        syncPort = syncPort,
        storePort = storePort,
        pageSize = pageSize,
    )

    @Test
    fun `다음 페이지가 남으면 CONTINUABLE을 반환하고 페이지 번호를 올린다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns page(listOf(record("1")), totalCount = 2_500)
        every { storePort.upsertAll(any(), any()) } returns 1

        val execution = stepExecution()
        val status = run(tasklet(), execution)

        assertEquals(RepeatStatus.CONTINUABLE, status)
        assertEquals(2, execution.executionContext.getInt(SpotSyncTasklet.KEY_NEXT_PAGE))
    }

    @Test
    fun `마지막 페이지면 FINISHED를 반환한다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns page(listOf(record("1")), totalCount = 1)
        every { storePort.upsertAll(any(), any()) } returns 1

        val status = run(tasklet(), stepExecution())

        assertEquals(RepeatStatus.FINISHED, status)
    }

    @Test
    fun `ExecutionContext에 남은 페이지 번호부터 재개한다`() {
        // AC-8: 3페이지에서 실패한 실행을 재실행하면 1페이지가 아니라 3페이지부터 처리한다.
        every { syncPort.fetchPage(DomainLocale.KO, 3, 1_000) } returns page(listOf(record("1")), totalCount = 3_000)
        every { storePort.upsertAll(any(), any()) } returns 1

        val execution = stepExecution()
        execution.executionContext.putInt(SpotSyncTasklet.KEY_NEXT_PAGE, 3)
        run(tasklet(), execution)

        verify(exactly = 1) { syncPort.fetchPage(DomainLocale.KO, 3, 1_000) }
        verify(exactly = 0) { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) }
    }

    @Test
    fun `실행 시각은 첫 페이지에서 고정되어 이후 페이지에서 바뀌지 않는다`() {
        // 페이지마다 now()를 쓰면 정리 단계(R-5)의 기준 시각이 페이지별로 어긋나
        // "이번 실행이 건드린 행"의 경계가 흐려진다.
        every { syncPort.fetchPage(DomainLocale.KO, any(), 1_000) } returns
            page(listOf(record("1")), totalCount = 3_000)
        val syncedAt = slot<Instant>()
        every { storePort.upsertAll(any(), capture(syncedAt)) } returns 1

        val execution = stepExecution()
        run(tasklet(), execution)
        val first = syncedAt.captured

        run(tasklet(), execution)
        val second = syncedAt.captured

        assertEquals(first, second)
    }

    @Test
    fun `언어별 처리 건수를 페이지에 걸쳐 누적한다`() {
        // AC-18: 실행 이력에서 언어별 처리 건수를 확인할 수 있어야 한다.
        every { syncPort.fetchPage(DomainLocale.KO, any(), 1_000) } returns
            page(listOf(record("1"), record("2")), totalCount = 3_000)
        every { storePort.upsertAll(any(), any()) } returns 2

        val execution = stepExecution()
        run(tasklet(), execution)
        run(tasklet(), execution)

        assertEquals(4, execution.executionContext.getInt(SpotSyncTasklet.KEY_WRITTEN))
    }

    @Test
    fun `카테고리 폴백 건수를 누적해 설정 오류를 관측 가능하게 한다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns SpotSyncPage(
            records = listOf(record("1")),
            totalCount = 1,
            droppedCount = 2,
            categoryFallbackCount = 5,
        )
        every { storePort.upsertAll(any(), any()) } returns 1

        val execution = stepExecution()
        run(tasklet(), execution)

        assertEquals(5, execution.executionContext.getInt(SpotSyncTasklet.KEY_CATEGORY_FALLBACK))
        assertEquals(2, execution.executionContext.getInt(SpotSyncTasklet.KEY_DROPPED))
    }

    @Test
    fun `upstream 예외는 삼키지 않고 전파해 해당 페이지를 실패로 남긴다`() {
        // AC-7·AC-9: 페이지 실패가 앞 페이지 커밋을 되돌리지 않고, 무한 대기하지 않는다.
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } throws IllegalStateException("upstream down")

        val execution = stepExecution()
        val thrown = runCatching { run(tasklet(), execution) }.exceptionOrNull()

        assertNotEquals(null, thrown)
    }
}
