package org.around.ieuri.tourism.adapter.batch

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.shared.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.spot.provided.FestivalSweepResult
import org.around.ieuri.tourism.application.spot.provided.SpotSyncPageResult
import org.around.ieuri.tourism.application.spot.provided.SpotSyncUseCase
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotHarvest
import org.around.ieuri.tourism.domain.spot.SweepBlockedException
import org.junit.jupiter.api.assertThrows
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** 동기화 Tasklet의 페이지 진행·재개·정리 호출(SPEC-0004 R-3·R-4, SPEC-0007). 정리 판정은 도메인(`SpotHarvest`)이 한다. */
class SpotSyncTaskletTest {

    private val useCase = mockk<SpotSyncUseCase>()

    private fun page(vararg ids: String, hasMore: Boolean, written: Int = ids.size, dropped: Int = 0, fallback: Int = 0) = SpotSyncPageResult(written = written, contentIds = ids.toList(), hasMore = hasMore, dropped = dropped, categoryFallback = fallback)

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder().addString(RUN_DATE_PARAMETER, RUN_DATE.toString()).toJobParameters()
        return StepExecution(1L, "syncLanguageStep-KO", JobExecution(1L, JobInstance(1L, "spotSyncJob"), parameters))
    }

    private fun run(tasklet: SpotSyncTasklet, stepExecution: StepExecution): RepeatStatus? = tasklet.execute(StepContribution(stepExecution), ChunkContext(StepContext(stepExecution)))

    private fun tasklet(target: SpotSyncTasklet.Target = SpotSyncTasklet.Target.SPOT) = SpotSyncTasklet(DomainLocale.KO, target, useCase, PAGE_SIZE)

    @Test
    fun `다음 페이지가 남으면 CONTINUABLE이고 페이지 번호를 올린다`() {
        every { useCase.syncSpotPage(DomainLocale.KO, 1, PAGE_SIZE, any(), RUN_DATE) } returns page("a", hasMore = true)
        val step = stepExecution()

        assertEquals(RepeatStatus.CONTINUABLE, run(tasklet(), step))
        assertEquals(2, step.executionContext.getInt(SpotSyncTasklet.KEY_NEXT_PAGE))
        verify(exactly = 0) { useCase.sweepSpots(any(), any()) }
    }

    @Test
    fun `마지막 페이지에서 본 contentId 전부로 정리하고 FINISHED다`() {
        every { useCase.syncSpotPage(DomainLocale.KO, 1, PAGE_SIZE, any(), RUN_DATE) } returns page("a", "b", hasMore = true)
        every { useCase.syncSpotPage(DomainLocale.KO, 2, PAGE_SIZE, any(), RUN_DATE) } returns page("c", hasMore = false)
        val harvest = slot<SpotHarvest>()
        every { useCase.sweepSpots(DomainLocale.KO, capture(harvest)) } returns 5
        val tasklet = tasklet()
        val step = stepExecution()

        run(tasklet, step)
        val status = run(tasklet, step)

        assertEquals(RepeatStatus.FINISHED, status)
        assertEquals(SpotHarvest(setOf("a", "b", "c"), resumed = false), harvest.captured)
        assertEquals(5, step.executionContext.getInt(SpotSyncTasklet.KEY_DELETED))
        assertEquals(3, step.executionContext.getInt(SpotSyncTasklet.KEY_COLLECTED))
    }

    @Test
    fun `남은 페이지 번호부터 재개하고, 재개 실행이라고 정리에 알린다`() {
        every { useCase.syncSpotPage(DomainLocale.KO, 3, PAGE_SIZE, any(), RUN_DATE) } returns page("z", hasMore = false)
        val harvest = slot<SpotHarvest>()
        every { useCase.sweepSpots(DomainLocale.KO, capture(harvest)) } throws SweepBlockedException("resumed run (incomplete id set)")
        val step = stepExecution()
        step.executionContext.putInt(SpotSyncTasklet.KEY_NEXT_PAGE, 3)

        assertEquals(RepeatStatus.FINISHED, run(tasklet(), step))
        assertEquals(true, harvest.captured.resumed)
        assertEquals("resumed run (incomplete id set)", step.executionContext.getString(SpotSyncTasklet.KEY_CLEANUP_SKIPPED))
    }

    @Test
    fun `실행 시각은 첫 페이지에서 고정된다`() {
        val syncedAt = mutableListOf<Instant>()
        every { useCase.syncSpotPage(DomainLocale.KO, any(), PAGE_SIZE, capture(syncedAt), RUN_DATE) } returnsMany
            listOf(page("a", hasMore = true), page("b", hasMore = true))
        val tasklet = tasklet()
        val step = stepExecution()

        run(tasklet, step)
        run(tasklet, step)

        assertEquals(syncedAt[0], syncedAt[1])
    }

    @Test
    fun `적재·드랍·폴백 건수를 페이지에 걸쳐 누적한다`() {
        every { useCase.syncSpotPage(DomainLocale.KO, 1, PAGE_SIZE, any(), RUN_DATE) } returns page("a", hasMore = true, written = 4, dropped = 1, fallback = 2)
        every { useCase.syncSpotPage(DomainLocale.KO, 2, PAGE_SIZE, any(), RUN_DATE) } returns page("b", hasMore = true, written = 3, dropped = 5, fallback = 1)
        val tasklet = tasklet()
        val step = stepExecution()

        run(tasklet, step)
        run(tasklet, step)

        assertEquals(7, step.executionContext.getInt(SpotSyncTasklet.KEY_WRITTEN))
        assertEquals(6, step.executionContext.getInt(SpotSyncTasklet.KEY_DROPPED))
        assertEquals(3, step.executionContext.getInt(SpotSyncTasklet.KEY_CATEGORY_FALLBACK))
    }

    @Test
    fun `upstream 예외는 삼키지 않고 전파한다`() {
        every { useCase.syncSpotPage(any(), any(), any(), any(), any()) } throws UpstreamUnavailableException("upstream down")

        assertThrows<UpstreamUnavailableException> { run(tasklet(), stepExecution()) }
    }

    @Test
    fun `축제 Step은 실행일로 축제 페이지를 받고 끝난 축제와 사라진 축제를 정리한다`() {
        every { useCase.syncFestivalPage(DomainLocale.KO, 1, PAGE_SIZE, any(), RUN_DATE) } returns page("f1", hasMore = false)
        every { useCase.sweepFestivals(DomainLocale.KO, SpotHarvest(setOf("f1"), resumed = false), RUN_DATE) } returns FestivalSweepResult(ended = 2, missing = 3)
        val step = stepExecution()

        assertEquals(RepeatStatus.FINISHED, run(tasklet(SpotSyncTasklet.Target.FESTIVAL), step))
        assertEquals(5, step.executionContext.getInt(SpotSyncTasklet.KEY_DELETED))
        assertEquals(2, step.executionContext.getInt(SpotSyncTasklet.KEY_DELETED_ENDED))
        verify(exactly = 0) { useCase.syncSpotPage(any(), any(), any(), any(), any()) }
    }

    private companion object {
        const val PAGE_SIZE = 100
        val RUN_DATE: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
