package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.application.port.out.persistence.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRegion
import org.around.ieuri.tourism.infrastructure.batch.RUN_DATE_PARAMETER
import org.around.ieuri.tourism.infrastructure.batch.SyncContext
import org.around.ieuri.tourism.infrastructure.batch.TourStatsSyncTasklet
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

/** KTO 빅데이터 Step의 순회·페이지 루프와 관측값(SPEC-0015 R4~R6 · R8 · R9). StepExecution만 직접 만든다. */
class TourStatsSyncTaskletTest {

    private val runDate = LocalDate.of(2026, 8, 11)
    private val districtCodePort = mockk<DistrictCodeQueryPort>()

    /** 적재된 레코드 대역. 시군구 코드만 있으면 R9 필터를 통과하는지 볼 수 있다. */
    private data class Row(val code: String)

    private val regions = listOf(
        DistrictRegion(code = "11110", provinceCode = "11"),
        DistrictRegion(code = "11140", provinceCode = "11"),
        DistrictRegion(code = "26410", provinceCode = "26"),
    )

    private fun page(rows: List<Row>, totalCount: Int, dropped: Int = 0) = TourStatsSyncPage(records = rows, totalCount = totalCount, droppedCount = dropped)

    private fun stepExecution(): StepExecution {
        val parameters = JobParametersBuilder()
            .addString(RUN_DATE_PARAMETER, runDate.toString())
            .toJobParameters()
        return StepExecution(
            1L,
            "tourStatsSyncStep",
            JobExecution(1L, JobInstance(1L, "tourStatsConcentrationRateSyncJob"), parameters),
        )
    }

    private fun <R : Any> run(tasklet: TourStatsSyncTasklet<Row, R>, execution: StepExecution): RepeatStatus? = tasklet.execute(StepContribution(execution), ChunkContext(StepContext(execution)))

    /** 끝날 때까지 돌린다 — 순회가 여러 번의 execute로 나뉜다. */
    private fun <R : Any> runToCompletion(tasklet: TourStatsSyncTasklet<Row, R>, execution: StepExecution): Int {
        var calls = 0
        while (run(tasklet, execution) == RepeatStatus.CONTINUABLE) {
            calls++
            check(calls < 100) { "루프가 끝나지 않습니다" }
        }
        return calls + 1
    }

    private fun tasklet(
        pageSize: Int = 100,
        fetchPage: (SyncContext, DistrictRegion, Int) -> TourStatsSyncPage<Row>,
        upsert: (List<Row>) -> Int = { it.size },
        targetsOf: (DistrictCodeQueryPort) -> List<DistrictRegion> = { regions },
    ) = TourStatsSyncTasklet(
        datasetName = "test",
        pageSize = pageSize,
        districtCodePort = districtCodePort,
        fetchPage = fetchPage,
        districtCodeOf = Row::code,
        upsert = upsert,
        targetsOf = targetsOf,
    )

    // ---------- 순회 단위 ----------

    @Test
    fun `집중률은 지역마다 시도 코드와 시군구 코드를 함께 넘겨 호출한다`() {
        // 집중률만 signguCd가 필수다.
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val seen = mutableListOf<Pair<String, String>>()

        val tasklet = tasklet(fetchPage = { _, region, _ ->
            seen += region.provinceCode to region.code
            page(emptyList(), totalCount = 0)
        })

        runToCompletion(tasklet, stepExecution())

        assertEquals(listOf("11" to "11110", "11" to "11140", "26" to "26410"), seen)
    }

    @Test
    fun `강도는 시도 코드만 순회해 시군구 수보다 적게 호출한다`() {
        // areaCd만 주면 그 시도의 시군구 전체가 온다 — 17 시도만 돈다(여기서는 3 시군구 → 2 시도).
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val provinces = listOf("11", "26")
        val seen = mutableListOf<String>()

        val tasklet = TourStatsSyncTasklet<Row, String>(
            datasetName = "stayIntensity",
            pageSize = 100,
            districtCodePort = districtCodePort,
            fetchPage = { _, areaCd, _ ->
                seen += areaCd
                // 한 시도 응답에 그 시도의 시군구가 모두 실려 온다.
                page(regions.filter { it.provinceCode == areaCd }.map { Row(it.code) }, totalCount = 2)
            },
            districtCodeOf = Row::code,
            upsert = { it.size },
            targetsOf = { provinces },
        )

        val execution = stepExecution()
        runToCompletion(tasklet, execution)

        assertEquals(provinces, seen)
        assertEquals(3, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_WRITTEN))
    }

    @Test
    fun `시도 응답에 섞인 시도 집계 행은 R9 필터가 걸러낸다`() {
        // 시도 집계 행(2자리 코드)은 별도 분기 없이 R9 필터에 걸린다.
        every { districtCodePort.findAllCodes() } returns setOf("11110", "11140")
        val upserted = mutableListOf<List<Row>>()

        val tasklet = TourStatsSyncTasklet<Row, String>(
            datasetName = "stayIntensity",
            pageSize = 100,
            districtCodePort = districtCodePort,
            fetchPage = { _, areaCd, _ ->
                page(listOf(Row("11110"), Row("11140"), Row(areaCd)), totalCount = 3)
            },
            districtCodeOf = Row::code,
            upsert = {
                upserted += it
                it.size
            },
            targetsOf = { listOf("11") },
        )

        val execution = stepExecution()
        runToCompletion(tasklet, execution)

        assertEquals(listOf(listOf(Row("11110"), Row("11140"))), upserted)
        assertEquals(1, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_SKIPPED_UNKNOWN_CODE))
    }

    @Test
    fun `한 지역에 페이지가 남으면 같은 지역의 다음 페이지를 먼저 부른다`() {
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val seen = mutableListOf<Pair<String, Int>>()

        // 첫 지역만 3페이지(총 250행 / 페이지 100), 나머지는 1페이지.
        val tasklet = tasklet(fetchPage = { _, region, pageNo ->
            seen += region.code to pageNo
            val total = if (region.code == "11110") 250 else 1
            page(listOf(Row(region.code)), totalCount = total)
        })

        runToCompletion(tasklet, stepExecution())

        assertEquals(
            listOf("11110" to 1, "11110" to 2, "11110" to 3, "11140" to 1, "26410" to 1),
            seen,
        )
    }

    @Test
    fun `순회 대상이 하나뿐이면 순회 없이 페이지만 돈다`() {
        // 방문자수는 지역 필터가 없어 NO_REGION 한 건으로 돈다.
        every { districtCodePort.findAllCodes() } returns setOf("11110")
        val pages = mutableListOf<Int>()

        val tasklet = TourStatsSyncTasklet<Row, Unit>(
            datasetName = "visitorCount",
            pageSize = 100,
            districtCodePort = districtCodePort,
            fetchPage = { _, _, pageNo ->
                pages += pageNo
                page(listOf(Row("11110")), totalCount = 150)
            },
            districtCodeOf = Row::code,
            upsert = { it.size },
            targetsOf = { TourStatsSyncTasklet.NO_REGION },
        )

        runToCompletion(tasklet, stepExecution())

        assertEquals(listOf(1, 2), pages)
    }

    // ---------- 재개 (ExecutionContext) ----------

    @Test
    fun `중단 지점의 지역 인덱스와 페이지 번호에서 재개한다`() {
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val seen = mutableListOf<Pair<String, Int>>()

        val tasklet = tasklet(fetchPage = { _, region, pageNo ->
            seen += region.code to pageNo
            page(listOf(Row(region.code)), totalCount = 1)
        })

        val execution = stepExecution()
        // 두 번째 지역의 2페이지에서 실패했던 실행을 흉내 낸다.
        execution.executionContext.putInt(TourStatsSyncTasklet.KEY_REGION_INDEX, 1)
        execution.executionContext.putInt(TourStatsSyncTasklet.KEY_NEXT_PAGE, 2)

        runToCompletion(tasklet, execution)

        // 첫 지역은 이미 끝났으므로 다시 부르지 않는다.
        assertEquals(listOf("11140" to 2, "26410" to 1), seen)
    }

    @Test
    fun `한 지역을 끝내면 다음 지역의 1페이지부터 시작한다`() {
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()

        val tasklet = tasklet(fetchPage = { _, region, _ ->
            page(listOf(Row(region.code)), totalCount = if (region.code == "11110") 150 else 1)
        })

        val execution = stepExecution()
        run(tasklet, execution) // 11110 p1 → 페이지 남음
        run(tasklet, execution) // 11110 p2 → 끝
        run(tasklet, execution) // 11140 p1

        assertEquals(2, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_REGION_INDEX))
        assertEquals(1, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_NEXT_PAGE))
    }

    @Test
    fun `마지막 지역을 끝내야 FINISHED가 된다`() {
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()

        val tasklet = tasklet(fetchPage = { _, region, _ ->
            page(listOf(Row(region.code)), totalCount = 1)
        })

        val execution = stepExecution()
        assertEquals(RepeatStatus.CONTINUABLE, run(tasklet, execution))
        assertEquals(RepeatStatus.CONTINUABLE, run(tasklet, execution))
        assertEquals(RepeatStatus.FINISHED, run(tasklet, execution))
    }

    @Test
    fun `기준일은 잡 파라미터에서 오며 fetchPage에 그대로 전달된다`() {
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val seen = mutableListOf<LocalDate>()

        val tasklet = tasklet(fetchPage = { context, _, _ ->
            seen += context.runDate
            page(emptyList(), totalCount = 0)
        })

        runToCompletion(tasklet, stepExecution())

        assertEquals(List(3) { runDate }, seen)
    }

    // ---------- 빈 응답 (R8) ----------

    @Test
    fun `한 지역이 빈 응답이어도 예외 없이 다음 지역으로 넘어간다`() {
        // 부재는 장애가 아니다.
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()
        val seen = mutableListOf<String>()

        val tasklet = tasklet(fetchPage = { _, region, _ ->
            seen += region.code
            page(emptyList(), totalCount = 0)
        })

        val execution = stepExecution()
        runToCompletion(tasklet, execution)

        assertEquals(regions.map { it.code }, seen)
        assertEquals(0, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_WRITTEN))
    }

    // ---------- R9 필터와 관측값 ----------

    @Test
    fun `미등록 시군구 코드 행은 건너뛰고 건수를 누적한다`() {
        // R9. 예외를 던지지 않는다 — 미등록 코드가 있어도 정상 데이터는 쌓인다.
        every { districtCodePort.findAllCodes() } returns setOf("11110")
        val upserted = mutableListOf<List<Row>>()

        val tasklet = tasklet(
            fetchPage = { _, _, _ ->
                page(listOf(Row("11110"), Row("99999")), totalCount = 1, dropped = 2)
            },
            upsert = {
                upserted += it
                it.size
            },
        )

        val execution = stepExecution()
        runToCompletion(tasklet, execution)

        // 지역 3곳 × (통과 1 + 미등록 1)
        assertEquals(List(3) { listOf(Row("11110")) }, upserted)
        assertEquals(3, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_WRITTEN))
        assertEquals(3, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_SKIPPED_UNKNOWN_CODE))
        assertEquals(6, execution.executionContext.getInt(TourStatsSyncTasklet.KEY_DROPPED))
    }

    @Test
    fun `등록 코드 집합은 실행당 한 번만 조회한다`() {
        // 코드 집합은 Step 시작 시 한 번만 읽는다.
        every { districtCodePort.findAllCodes() } returns regions.map { it.code }.toSet()

        val tasklet = tasklet(fetchPage = { _, region, _ ->
            page(listOf(Row(region.code)), totalCount = 1)
        })

        runToCompletion(tasklet, stepExecution())

        verify(exactly = 1) { districtCodePort.findAllCodes() }
    }

    @Test
    fun `순회 대상이 비어 있으면 호출 없이 종료한다`() {
        // district 테이블이 아직 비어 있는 상태. 인덱스 접근이 터지면 안 된다.
        val tasklet = tasklet(
            fetchPage = { _, _, _ -> error("호출되면 안 됩니다") },
            targetsOf = { emptyList() },
        )

        assertEquals(RepeatStatus.FINISHED, run(tasklet, stepExecution()))
        verify(exactly = 0) { districtCodePort.findAllCodes() }
    }
}
