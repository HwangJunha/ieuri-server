package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 상세 사전 수집 단계(SPEC-0037). 한 갈래를 `spot.id` 키셋 커서로 돈다.
 * - 하루 처리 건수(=호출 수)를 [dailyLimit]으로 제한한다.
 */
class SpotDetailPrefetchTasklet(
    private val useCase: SpotDetailPrefetchUseCase,
    private val part: SpotDetailPart,
    private val batchSize: Int,
    private val dailyLimit: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val context = chunkContext.stepContext.stepExecution.executionContext
        val done = context.getInt(KEY_DONE, 0)
        val remaining = dailyLimit - done
        if (remaining <= 0) {
            log.warn("{} 오늘 몫 {}건을 다 썼다", part, dailyLimit)
            return RepeatStatus.FINISHED
        }

        val result = try {
            useCase.prefetchNext(part, context.getLong(KEY_CURSOR, 0L), minOf(batchSize, remaining))
        } catch (e: UpstreamRateLimitedException) {
            // 그날 쿼터가 끝났다. 남은 장소는 다음 실행이 다시 집는다(AC-014·AC-015).
            log.warn("{} 일일 한도 도달({}건 처리 후)", part, done)
            return RepeatStatus.FINISHED
        }

        // 성공 건수가 아니라 처리 건수로 소진을 판정한다.
        if (result.processed == 0) {
            log.info("{} 상세 수집 완료: 처리 {}건, 실패 {}건", part, done, context.getInt(KEY_FAILED, 0))
            return RepeatStatus.FINISHED
        }

        context.putLong(KEY_CURSOR, result.lastSpotId)
        context.putInt(KEY_DONE, done + result.processed)
        context.putInt(KEY_FAILED, context.getInt(KEY_FAILED, 0) + result.failed)
        contribution.incrementWriteCount((result.processed - result.failed).toLong())
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_CURSOR = "cursor"
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
    }
}
