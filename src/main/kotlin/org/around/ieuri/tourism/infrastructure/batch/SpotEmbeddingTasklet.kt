package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotEmbeddingSyncUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 장소 임베딩 적재 단계(SPEC-0035 S2). 번역 행 id 키셋 커서로 한 묶음씩 유스케이스를 부른다.
 * 제공자 실패는 단계를 실패시키지 않는다 — 실패 건수만 남기고 커서를 옮긴다(다음 실행의 대상으로 남는다).
 */
class SpotEmbeddingTasklet(
    private val useCase: SpotEmbeddingSyncUseCase,
    private val batchSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val cursor = executionContext.getLong(KEY_CURSOR, 0L)
        val result = useCase.syncNext(cursor, batchSize)

        if (result.processed == 0) {
            log.info(
                "spot embedding finished: embedded={} failed={}",
                executionContext.getInt(KEY_EMBEDDED, 0), executionContext.getInt(KEY_FAILED, 0),
            )
            return RepeatStatus.FINISHED
        }
        executionContext.putLong(KEY_CURSOR, result.lastTranslationId)
        executionContext.putInt(KEY_EMBEDDED, executionContext.getInt(KEY_EMBEDDED, 0) + result.embedded)
        executionContext.putInt(KEY_FAILED, executionContext.getInt(KEY_FAILED, 0) + result.failed)
        contribution.incrementWriteCount(result.embedded.toLong())
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_CURSOR = "embeddingCursor"
        const val KEY_EMBEDDED = "embedded"
        const val KEY_FAILED = "failed"
    }
}
