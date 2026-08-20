package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPage
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotUpsertPort
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.batch.RUN_DATE_PARAMETER
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncTasklet
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 동기화 Tasklet의 페이지 진행·재개 동작(SPEC-0004 R-3·R-4).
 *
 * Spring 컨텍스트를 띄우지 않고 StepExecution만 직접 만들어 검증한다 — 검증 대상이 프레임워크 배선이
 * 아니라 **페이지 루프와 ExecutionContext에 남는 재개 상태**이기 때문이다.
 */
class SpotSyncTaskletTest {

    private val syncPort = mockk<SpotSyncPort>()
    private val storePort = mockk<SpotStorePort>(relaxed = true)
    private val upsertPort = mockk<SpotUpsertPort>(relaxed = true)

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

    /**
     * 실제 StepExecution — ExecutionContext가 진짜로 유지되어야 재개 동작을 검증할 수 있다.
     * `runDate` 잡 파라미터는 SPEC-0007의 종료 축제 제외 집합 조회 기준일이다.
     */
    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addString(RUN_DATE_PARAMETER, RUN_DATE.toString())
            .toJobParameters()
        return StepExecution("syncLanguageStep-KO", JobExecution(1L, JobInstance(1L, "spotSyncJob"), parameters))
    }

    private fun run(tasklet: SpotSyncTasklet, stepExecution: StepExecution): RepeatStatus? {
        val contribution = StepContribution(stepExecution)
        val chunkContext = ChunkContext(StepContext(stepExecution))
        return tasklet.execute(contribution, chunkContext)
    }

    private fun tasklet(
        pageSize: Int = 1_000,
        endedFestivalIds: Set<String> = emptySet(),
    ) = SpotSyncTasklet(
        language = DomainLocale.KO,
        syncPort = syncPort,
        storePort = storePort,
        upsertPort = upsertPort,
        pageSize = pageSize,
        endedFestivalIds = { endedFestivalIds },
    )

    @Test
    fun `다음 페이지가 남으면 CONTINUABLE을 반환하고 페이지 번호를 올린다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns page(listOf(record("1")), totalCount = 2_500)
        every { upsertPort.upsertAll(any(), any()) } returns 1

        val execution = stepExecution()
        val status = run(tasklet(), execution)

        assertEquals(RepeatStatus.CONTINUABLE, status)
        assertEquals(2, execution.executionContext.getInt(SpotSyncTasklet.KEY_NEXT_PAGE))
    }

    @Test
    fun `마지막 페이지면 FINISHED를 반환한다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns page(listOf(record("1")), totalCount = 1)
        every { upsertPort.upsertAll(any(), any()) } returns 1

        val status = run(tasklet(), stepExecution())

        assertEquals(RepeatStatus.FINISHED, status)
    }

    @Test
    fun `ExecutionContext에 남은 페이지 번호부터 재개한다`() {
        // AC-8: 3페이지에서 실패한 실행을 재실행하면 1페이지가 아니라 3페이지부터 처리한다.
        every { syncPort.fetchPage(DomainLocale.KO, 3, 1_000) } returns page(listOf(record("1")), totalCount = 3_000)
        every { upsertPort.upsertAll(any(), any()) } returns 1

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
        every { upsertPort.upsertAll(any(), capture(syncedAt)) } returns 1

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
        every { upsertPort.upsertAll(any(), any()) } returns 2

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
        every { upsertPort.upsertAll(any(), any()) } returns 1

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

    // ---------- 사라진 항목 정리 (R-5) ----------

    @Test
    fun `수집 집합에 없는 행을 삭제한다`() {
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns
            page(listOf(record("a"), record("b")), totalCount = 2)
        every { upsertPort.upsertAll(any(), any()) } returns 2
        every { storePort.deleteMissing(any(), any()) } returns 1

        run(tasklet(), stepExecution())

        // 수집한 것(a, b)만 살리고 나머지는 지운다 — 시각이 아니라 집합 기준이다.
        verify(exactly = 1) { storePort.deleteMissing(DomainLocale.KO, setOf("a", "b")) }
    }

    @Test
    fun `수집이 0건이면 정리를 건너뛴다(AC-14)`() {
        // 업스트림 일시 오류로 빈 목록을 받았을 때 전량 삭제되면 안 된다.
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns page(emptyList(), totalCount = 0)
        every { upsertPort.upsertAll(any(), any()) } returns 0

        val execution = stepExecution()
        run(tasklet(), execution)

        verify(exactly = 0) { storePort.deleteMissing(any(), any()) }
        assertEquals("collected 0 items", execution.executionContext.getString(SpotSyncTasklet.KEY_CLEANUP_SKIPPED))
    }

    @Test
    fun `재개 실행이면 집합이 불완전하므로 정리를 건너뛴다`() {
        // 3페이지부터 재개하면 1·2페이지 항목이 집합에 없어, 그대로 지우면 대량 오삭제가 된다.
        every { syncPort.fetchPage(DomainLocale.KO, 3, 1_000) } returns
            page(listOf(record("c")), totalCount = 3_000)
        every { upsertPort.upsertAll(any(), any()) } returns 1

        val execution = stepExecution()
        execution.executionContext.putInt(SpotSyncTasklet.KEY_NEXT_PAGE, 3)
        // totalCount를 넘겨 마지막 페이지로 만든다.
        every { syncPort.fetchPage(DomainLocale.KO, 3, 1_000) } returns
            page(listOf(record("c")), totalCount = 3)
        run(tasklet(), execution)

        verify(exactly = 0) { storePort.deleteMissing(any(), any()) }
        assertTrue(
            execution.executionContext.getString(SpotSyncTasklet.KEY_CLEANUP_SKIPPED).contains("resumed"),
        )
    }

    @Test
    fun `정리는 마지막 페이지에서 한 번만 수행된다`() {
        every { syncPort.fetchPage(DomainLocale.KO, any(), 1_000) } returns
            page(listOf(record("a")), totalCount = 3_000)
        every { upsertPort.upsertAll(any(), any()) } returns 1
        every { storePort.deleteMissing(any(), any()) } returns 0

        val execution = stepExecution()
        run(tasklet(), execution) // 아직 페이지가 남음
        verify(exactly = 0) { storePort.deleteMissing(any(), any()) }
    }

    // ---------- 종료 축제 재삽입 방지 (SPEC-0007 R-9) ----------

    @Test
    fun `종료된 축제는 적재 대상에서 제외한다`() {
        // AC-31·AC-32. areaBasedSyncList2에는 contentTypeId 파라미터가 없어 축제도 이 경로로 온다.
        // 제외하지 않으면 같은 잡의 축제 정리가 지운 행사를 이 Step이 되살린다.
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns
            page(listOf(record("ended"), record("alive")), totalCount = 2)
        every { upsertPort.upsertAll(any(), any()) } returns 1
        every { storePort.deleteMissing(any(), any()) } returns 0

        run(tasklet(endedFestivalIds = setOf("ended")), stepExecution())

        verify(exactly = 1) { upsertPort.upsertAll(match { it.map(SyncSpotCommand::contentId) == listOf("alive") }, any()) }
    }

    @Test
    fun `축제가 아닌 항목은 종료 판정의 영향을 받지 않는다`() {
        // AC-33. 판정은 contentId 집합 멤버십뿐이다 — 카테고리·날짜를 여기서 다시 해석하지 않으므로
        // 축제가 아닌 contentId는 애초에 제외 집합에 들어올 수 없다.
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns
            page(listOf(record("spot-1"), record("spot-2")), totalCount = 2)
        every { upsertPort.upsertAll(any(), any()) } returns 2
        every { storePort.deleteMissing(any(), any()) } returns 0

        run(tasklet(endedFestivalIds = setOf("ended-festival")), stepExecution())

        verify(exactly = 1) { upsertPort.upsertAll(match { it.size == 2 }, any()) }
    }

    @Test
    fun `제외된 항목도 수집 집합에는 포함되어 이중 삭제되지 않는다`() {
        // AC-34. collectedIds는 deleteMissing의 생존 집합이다. 제외분이 빠지면 그것이 "사라진
        // 항목"으로 삭제 대상에 들어가 삭제 건수가 이중으로 잡힌다.
        every { syncPort.fetchPage(DomainLocale.KO, 1, 1_000) } returns
            page(listOf(record("ended"), record("alive")), totalCount = 2)
        every { upsertPort.upsertAll(any(), any()) } returns 1
        every { storePort.deleteMissing(any(), any()) } returns 0

        val execution = stepExecution()
        run(tasklet(endedFestivalIds = setOf("ended")), execution)

        verify(exactly = 1) { storePort.deleteMissing(DomainLocale.KO, setOf("ended", "alive")) }
        assertEquals(2, execution.executionContext.getInt(SpotSyncTasklet.KEY_COLLECTED))
    }

    @Test
    fun `제외 집합은 Step당 한 번만 조회한다`() {
        // 페이지마다 다시 조회하면 페이지당 질의가 하나씩 늘고, 하루 1회 배치에서 그만큼의 신선도가
        // 필요하지도 않다. 부팅 시점이 아니라 실행 시점에 잡는 것이 요점이다.
        every { syncPort.fetchPage(DomainLocale.KO, any(), 1_000) } returns
            page(listOf(record("a")), totalCount = 3_000)
        every { upsertPort.upsertAll(any(), any()) } returns 1

        var lookups = 0
        val tasklet = SpotSyncTasklet(
            language = DomainLocale.KO,
            syncPort = syncPort,
            storePort = storePort,
            upsertPort = upsertPort,
            pageSize = 1_000,
            endedFestivalIds = { lookups++; emptySet() },
        )

        val execution = stepExecution()
        run(tasklet, execution)
        run(tasklet, execution)

        assertEquals(1, lookups)
    }

    private companion object {
        val RUN_DATE: LocalDate = LocalDate.of(2026, 8, 3)
    }
}
