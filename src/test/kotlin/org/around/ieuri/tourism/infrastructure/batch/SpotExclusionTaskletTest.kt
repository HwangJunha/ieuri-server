package org.around.ieuri.tourism.infrastructure.batch

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.tourism.application.command.SpotActivationResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotActivationUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 판정 Step의 실패 격리와 관측(SPEC-0018 R10·R11).
 *
 * **Step 실행이 아니라 Tasklet 본체만 부른다** — Spring Batch를 다시 증명하는 것이 아니라,
 * 예외를 삼키고 정상 종료하는 우리 결정을 검사한다.
 */
class SpotExclusionTaskletTest {

    private val useCase = mockk<SpotActivationUseCase>()
    private val tasklet = SpotExclusionTasklet(useCase)

    private val stepExecution = StepExecution(
        SpotExclusionTasklet.STEP_NAME,
        JobExecution(1L, JobInstance(1L, "spotSyncJob"), JobParameters()),
    )
    private val chunkContext = ChunkContext(StepContext(stepExecution))
    private val contribution = StepContribution(stepExecution)

    private val executionContext get() = stepExecution.executionContext

    @Test
    fun `판정 건수를 실행 이력에 남긴다`() {
        every { useCase.applyExclusions() } returns
            SpotActivationResult(judged = 1_200, deactivated = 216, reactivated = 3)

        val status = tasklet.execute(contribution, chunkContext)

        assertEquals(RepeatStatus.FINISHED, status)
        assertEquals(1_200, executionContext.getInt(SpotExclusionTasklet.KEY_JUDGED))
        assertEquals(216, executionContext.getInt(SpotExclusionTasklet.KEY_DEACTIVATED))
        assertEquals(3, executionContext.getInt(SpotExclusionTasklet.KEY_REACTIVATED))
        assertNull(executionContext.get(SpotExclusionTasklet.KEY_JUDGEMENT_FAILED))
    }

    @Test
    fun `판정이 실패해도 Step은 정상 종료한다`() {
        // R11 — 판정 실패가 앞 Step들의 적재를 무효화해서는 안 된다. 판정은 멱등하므로
        // 다음 실행이 다시 시도한다.
        every { useCase.applyExclusions() } throws IllegalStateException("keyword config broken")

        val status = tasklet.execute(contribution, chunkContext)

        assertEquals(RepeatStatus.FINISHED, status)
        assertEquals(
            "IllegalStateException",
            executionContext.getString(SpotExclusionTasklet.KEY_JUDGEMENT_FAILED),
        )
    }

    @Test
    fun `실패 사유에 예외 메시지를 담지 않는다`() {
        // 드라이버·업스트림 메시지에 접속 정보가 실릴 수 있어 타입 이름만 남긴다.
        every { useCase.applyExclusions() } throws
            IllegalStateException("jdbc:postgresql://host/db?password=secret")

        tasklet.execute(contribution, chunkContext)

        val reason = executionContext.getString(SpotExclusionTasklet.KEY_JUDGEMENT_FAILED)
        assertTrue("secret" !in reason, "실패 사유에 예외 메시지가 실렸다: $reason")
    }

    @Test
    fun `판정이 실패하면 건수를 남기지 않는다`() {
        every { useCase.applyExclusions() } throws RuntimeException()

        tasklet.execute(contribution, chunkContext)

        assertNull(executionContext.get(SpotExclusionTasklet.KEY_DEACTIVATED))
        assertNull(executionContext.get(SpotExclusionTasklet.KEY_REACTIVATED))
    }
}
