package org.around.ieuri.tourism.adapter.batch

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.spot.provided.dto.SyncFestivalCommand
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPage
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPort
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.FestivalPeriod
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 축제 Step의 수집 루프·정리 게이트·관측값(SPEC-0007 R-3·R-6·R-8·R-10·R-11). StepExecution만 직접 만든다. */
class FestivalSyncTaskletTest {

    private val runDate = LocalDate.of(2026, 8, 3)
    private val syncPort = mockk<FestivalSyncPort>()
    private val spotRepository = mockk<SpotRepository>(relaxed = true)

    private fun record(id: String) = SyncFestivalCommand(
        contentId = id,
        language = DomainLocale.KO,
        period = FestivalPeriod(start = runDate, end = runDate.plusDays(7)),
        progressType = null,
        festivalType = null,
    )

    private fun page(records: List<SyncFestivalCommand>, totalCount: Int, dropped: Int = 0) = FestivalSyncPage(records = records, totalCount = totalCount, droppedCount = dropped)

    /** 실제 StepExecution — runDate 잡 파라미터가 실려 있어야 Step이 실행일을 읽을 수 있다. */
    private fun stepExecution(language: DomainLocale = DomainLocale.KO): StepExecution {
        val parameters = JobParametersBuilder()
            .addString(RUN_DATE_PARAMETER, runDate.toString())
            .toJobParameters()
        return StepExecution(
            1L,
            "festivalSyncStep-${language.name}",
            JobExecution(1L, JobInstance(1L, "spotSyncJob"), parameters),
        )
    }

    private fun run(tasklet: FestivalSyncTasklet, stepExecution: StepExecution): RepeatStatus? = tasklet.execute(StepContribution(stepExecution), ChunkContext(StepContext(stepExecution)))

    private fun tasklet(language: DomainLocale = DomainLocale.KO, pageSize: Int = 1_000) = FestivalSyncTasklet(
        language = language,
        syncPort = syncPort,
        spotRepository = spotRepository,
        pageSize = pageSize,
    )

    // ---------- 수집 (R-3) ----------

    @Test
    fun `수집 호출의 행사 시작일 파라미터로 실행일을 넘긴다`() {
        // AC-8. Step 안에서 now()를 부르지 않고 잡 파라미터의 runDate를 쓴다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns
            page(listOf(record("1")), totalCount = 1)
        every { spotRepository.upsertFestivals(any(), any()) } returns 1

        run(tasklet(), stepExecution())

        verify(exactly = 1) { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) }
    }

    @Test
    fun `다음 페이지가 남으면 CONTINUABLE을 반환하고 페이지 번호를 올린다`() {
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns
            page(listOf(record("1")), totalCount = 2_500)
        every { spotRepository.upsertFestivals(any(), any()) } returns 1

        val execution = stepExecution()
        val status = run(tasklet(), execution)

        assertEquals(RepeatStatus.CONTINUABLE, status)
        assertEquals(2, execution.executionContext.getInt(FestivalSyncTasklet.KEY_NEXT_PAGE))
    }

    @Test
    fun `마지막 페이지면 FINISHED를 반환한다`() {
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns
            page(listOf(record("1")), totalCount = 1)
        every { spotRepository.upsertFestivals(any(), any()) } returns 1

        assertEquals(RepeatStatus.FINISHED, run(tasklet(), stepExecution()))
    }

    @Test
    fun `upstream 예외는 전파해 해당 언어 Step만 실패로 남긴다`() {
        // AC-21. Step이 언어별로 분리되어 있어 앞 언어의 커밋은 그대로 남는다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } throws IllegalStateException("upstream down")

        val thrown = runCatching { run(tasklet(), stepExecution()) }.exceptionOrNull()

        assertNotEquals(null, thrown)
    }

    // ---------- 적재 (R-1·R-7) ----------

    @Test
    fun `수집 레코드를 그대로 저장 포트에 전달한다`() {
        // AC-1·AC-23~25. Step은 명령의 모양만 책임진다.
        val records = listOf(record("a"), record("b"))
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns page(records, totalCount = 2)
        every { spotRepository.upsertFestivals(any(), any()) } returns 2
        every { spotRepository.deleteEndedFestivals(any(), any()) } returns 0
        every { spotRepository.deleteMissingFestivals(any(), any()) } returns 0

        run(tasklet(), stepExecution())

        verify(exactly = 1) { spotRepository.upsertFestivals(records, any()) }
    }

    @Test
    fun `언어별로 독립된 수집 호출과 정리가 이루어진다`() {
        // AC-20·AC-22. 삭제 대상은 그 언어의 행으로 한정된다.
        every { syncPort.fetchPage(any(), runDate, 1, 1_000) } returns page(listOf(record("a")), totalCount = 1)
        every { spotRepository.upsertFestivals(any(), any()) } returns 1
        every { spotRepository.deleteEndedFestivals(any(), any()) } returns 0
        every { spotRepository.deleteMissingFestivals(any(), any()) } returns 0

        DomainLocale.entries.forEach { language ->
            run(tasklet(language), stepExecution(language))
        }

        DomainLocale.entries.forEach { language ->
            verify(exactly = 1) { syncPort.fetchPage(language, runDate, 1, 1_000) }
            verify(exactly = 1) { spotRepository.deleteMissingFestivals(language, any()) }
            verify(exactly = 1) { spotRepository.deleteEndedFestivals(language, runDate) }
        }
    }

    // ---------- 정리 (R-8·R-10) ----------

    @Test
    fun `종료된 축제와 수집 집합에 없는 축제를 모두 지운다`() {
        // AC-26~AC-30. 두 갈래 삭제가 모두 수행되고, 생존 집합은 이번에 수집한 것이다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns
            page(listOf(record("a"), record("b")), totalCount = 2)
        every { spotRepository.upsertFestivals(any(), any()) } returns 2
        every { spotRepository.deleteEndedFestivals(DomainLocale.KO, runDate) } returns 3
        every { spotRepository.deleteMissingFestivals(DomainLocale.KO, setOf("a", "b")) } returns 1

        val execution = stepExecution()
        run(tasklet(), execution)

        verify(exactly = 1) { spotRepository.deleteEndedFestivals(DomainLocale.KO, runDate) }
        verify(exactly = 1) { spotRepository.deleteMissingFestivals(DomainLocale.KO, setOf("a", "b")) }
        // AC-40. 삭제 건수가 실행 이력에 남는다.
        assertEquals(4, execution.executionContext.getInt(FestivalSyncTasklet.KEY_DELETED))
        assertEquals(3, execution.executionContext.getInt(FestivalSyncTasklet.KEY_DELETED_ENDED))
    }

    @Test
    fun `수집이 0건이면 정리를 건너뛰고 사유를 남긴다`() {
        // AC-35·AC-38. 업스트림 일시 오류로 빈 목록을 받으면 그 언어의 축제가 전량 삭제된다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns page(emptyList(), totalCount = 0)
        every { spotRepository.upsertFestivals(any(), any()) } returns 0

        val execution = stepExecution()
        run(tasklet(), execution)

        verify(exactly = 0) { spotRepository.deleteMissingFestivals(any(), any()) }
        verify(exactly = 0) { spotRepository.deleteEndedFestivals(any(), any()) }
        assertEquals(
            "collected 0 items",
            execution.executionContext.getString(FestivalSyncTasklet.KEY_CLEANUP_SKIPPED),
        )
    }

    @Test
    fun `기존 100건에 이번 수집이 10건이어도 정리를 수행한다`() {
        // AC-36. 축제는 급감 게이트가 없다(R-10) — 기존 건수를 조회하지도 않는다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 1, 1_000) } returns
            page(List(10) { record("f$it") }, totalCount = 10)
        every { spotRepository.upsertFestivals(any(), any()) } returns 10
        every { spotRepository.deleteEndedFestivals(any(), any()) } returns 0
        every { spotRepository.deleteMissingFestivals(any(), any()) } returns 90

        val execution = stepExecution()
        run(tasklet(), execution)

        verify(exactly = 1) { spotRepository.deleteMissingFestivals(DomainLocale.KO, any()) }
        assertEquals(90, execution.executionContext.getInt(FestivalSyncTasklet.KEY_DELETED))
    }

    @Test
    fun `재개 실행이면 집합이 불완전하므로 정리를 건너뛴다`() {
        // AC-37·AC-38. 3페이지부터 재개하면 앞 페이지 항목이 집합에 없어 그대로 지우면 오삭제다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, 3, 1_000) } returns
            page(listOf(record("c")), totalCount = 3)

        val execution = stepExecution()
        execution.executionContext.putInt(FestivalSyncTasklet.KEY_NEXT_PAGE, 3)
        run(tasklet(), execution)

        verify(exactly = 0) { spotRepository.deleteMissingFestivals(any(), any()) }
        verify(exactly = 0) { spotRepository.deleteEndedFestivals(any(), any()) }
        assertTrue(
            execution.executionContext.getString(FestivalSyncTasklet.KEY_CLEANUP_SKIPPED).contains("resumed"),
        )
    }

    @Test
    fun `정리는 마지막 페이지에서만 수행된다`() {
        // 수집 중간에 지우면 페이지 경계에서 방금 넣은 행을 지울 수 있다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, any(), 1_000) } returns
            page(listOf(record("a")), totalCount = 3_000)
        every { spotRepository.upsertFestivals(any(), any()) } returns 1

        run(tasklet(), stepExecution())

        verify(exactly = 0) { spotRepository.deleteMissingFestivals(any(), any()) }
        verify(exactly = 0) { spotRepository.deleteEndedFestivals(any(), any()) }
    }

    // ---------- 관측 (R-11) ----------

    @Test
    fun `적재 건수와 드랍 건수를 페이지에 걸쳐 구분해 누적한다`() {
        // AC-39·AC-41. 드랍은 적재 건수와 따로 센다.
        every { syncPort.fetchPage(DomainLocale.KO, runDate, any(), 1_000) } returns
            page(listOf(record("1"), record("2")), totalCount = 3_000, dropped = 3)
        every { spotRepository.upsertFestivals(any(), any()) } returns 2

        val execution = stepExecution()
        run(tasklet(), execution)
        run(tasklet(), execution)

        assertEquals(4, execution.executionContext.getInt(FestivalSyncTasklet.KEY_WRITTEN))
        assertEquals(6, execution.executionContext.getInt(FestivalSyncTasklet.KEY_DROPPED))
    }
}
