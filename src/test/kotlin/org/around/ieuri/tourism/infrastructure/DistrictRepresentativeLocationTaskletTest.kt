package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.around.ieuri.tourism.infrastructure.batch.DistrictRepresentativeLocationTasklet
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
import kotlin.test.assertFailsWith

/**
 * 대표 위치 선정 + 카테고리 집계 Step의 오케스트레이션(SPEC-0015 · V46 · V50).
 *
 * 검증 대상은 SQL이 아니라 **한 Step이 두 갱신을 모두, 정해진 순서로 수행하는가**다 — 둘의
 * 기준 시점이 어긋나면 대표 위치와 집계가 다른 날의 데이터가 된다.
 */
class DistrictRepresentativeLocationTaskletTest {

    private val updatePort = mockk<DistrictRepresentativeUpdatePort>()

    private fun stepExecution(): StepExecution =
        StepExecution(
            "districtRepresentativeLocationJobStep",
            JobExecution(1L, JobInstance(1L, "districtRepresentativeLocationJob"), JobParameters()),
        )

    private fun run(stepExecution: StepExecution): RepeatStatus? =
        DistrictRepresentativeLocationTasklet(
            updatePort = updatePort,
            cellSizeDegrees = 0.02,
            minSpotCount = 10,
        ).execute(StepContribution(stepExecution), ChunkContext(StepContext(stepExecution)))

    @Test
    fun `한 번 실행에 대표 위치와 카테고리 집계를 모두 갱신한다`() {
        every { updatePort.refreshRepresentativeLocations(0.02, 10) } returns
            DistrictRepresentativeRefreshResult(selected = 226, cleared = 3)
        every { updatePort.refreshCategoryMix() } returns 252

        val execution = stepExecution()
        val contribution = StepContribution(execution)
        val status = DistrictRepresentativeLocationTasklet(updatePort, 0.02, 10)
            .execute(contribution, ChunkContext(StepContext(execution)))

        assertEquals(RepeatStatus.FINISHED, status)
        // 226 + 3 + 252 — 세 갱신 건수가 모두 반영된다.
        assertEquals(481, contribution.writeCount)
    }

    @Test
    fun `설정값을 대표 위치 선정에 그대로 넘긴다`() {
        every { updatePort.refreshRepresentativeLocations(any(), any()) } returns
            DistrictRepresentativeRefreshResult(selected = 1, cleared = 0)
        every { updatePort.refreshCategoryMix() } returns 1

        DistrictRepresentativeLocationTasklet(updatePort, cellSizeDegrees = 0.05, minSpotCount = 25)
            .execute(StepContribution(stepExecution()), ChunkContext(StepContext(stepExecution())))

        verify(exactly = 1) { updatePort.refreshRepresentativeLocations(0.05, 25) }
    }

    @Test
    fun `대표 위치가 먼저, 카테고리 집계가 나중이다`() {
        every { updatePort.refreshRepresentativeLocations(any(), any()) } returns
            DistrictRepresentativeRefreshResult(selected = 0, cleared = 0)
        every { updatePort.refreshCategoryMix() } returns 0

        run(stepExecution())

        verifyOrder {
            updatePort.refreshRepresentativeLocations(any(), any())
            updatePort.refreshCategoryMix()
        }
    }

    @Test
    fun `대표 위치 선정이 실패하면 카테고리 집계를 돌리지 않는다 — 기준 시점이 갈리면 안 된다`() {
        every { updatePort.refreshRepresentativeLocations(any(), any()) } throws IllegalStateException("boom")
        every { updatePort.refreshCategoryMix() } returns 252

        assertFailsWith<IllegalStateException> { run(stepExecution()) }

        verify(exactly = 0) { updatePort.refreshCategoryMix() }
    }
}
