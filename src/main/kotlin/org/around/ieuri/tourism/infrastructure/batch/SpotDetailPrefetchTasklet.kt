package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 상세 사전 수집 단계(SPEC-0037). 한 갈래를 `spot.id` 키셋 커서로 끝까지 돈다.
 *
 * **갈래마다 Step이 하나씩이다** — 네 갈래의 진행이 서로 다르고, 한 갈래가 일일 한도에 걸려도
 * 나머지는 계속 돌아야 한다(AC-018). Step 단위 분리는 Spring Batch의 재시작 지점과도 맞물린다.
 *
 * 개별 장소의 실패는 단계를 실패시키지 않는다 — 행이 남지 않아 다음 실행이 다시 집는다.
 */
class SpotDetailPrefetchTasklet(
    private val useCase: SpotDetailPrefetchUseCase,
    private val part: SpotDetailPart,
    private val batchSize: Int,
    private val dailyCallBudget: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val context = chunkContext.stepContext.stepExecution.executionContext
        val cursor = context.getLong(KEY_CURSOR, 0L)

        // **예산을 다 쓰면 오늘은 여기까지.** upstream이 한도를 알려줄 때까지 쓰면 그 갈래의
        // 한도를 배치가 전부 소모해, 그날 사용자 조회가 upstream에 닿지 못한다(FR-010).
        val spent = context.getInt(KEY_CALLS, 0)
        if (spent >= dailyCallBudget) {
            log.warn("{} 일일 호출 예산 소진({}/{}), 오늘은 여기까지", part, spent, dailyCallBudget)
            return RepeatStatus.FINISHED
        }

        val result = useCase.prefetchNext(part, cursor, batchSize)

        // **처리 건수로 판정한다** — 성공 건수로 보면 한 묶음이 전부 실패일 때 소진으로 오판해
        // 그 갈래의 나머지를 통째로 건너뛴다.
        if (result.processed == 0) {
            log.info(
                "{} 상세 수집 완료: fetched={} empty={} failed={} skipped={} calls={}",
                part, context.getInt(KEY_FETCHED, 0), context.getInt(KEY_EMPTY, 0),
                context.getInt(KEY_FAILED, 0), context.getInt(KEY_SKIPPED, 0), context.getInt(KEY_CALLS, 0),
            )
            return RepeatStatus.FINISHED
        }

        context.putLong(KEY_CURSOR, result.lastSpotId)
        context.putInt(KEY_FETCHED, context.getInt(KEY_FETCHED, 0) + result.fetched)
        context.putInt(KEY_EMPTY, context.getInt(KEY_EMPTY, 0) + result.empty)
        context.putInt(KEY_FAILED, context.getInt(KEY_FAILED, 0) + result.failed)
        context.putInt(KEY_SKIPPED, context.getInt(KEY_SKIPPED, 0) + result.skipped)
        context.putInt(KEY_CALLS, spent + result.calls)
        contribution.incrementWriteCount(result.fetched.toLong())

        // 일일 한도에 닿았다. **다음 실행은 커서가 아니라 대상 쿼리로 재개한다** — runDate가 식별
        // 파라미터라 다음 날은 새 JobInstance이고 커서는 0부터다. 확보된 행은 쿼리가 걸러 내므로
        // 건너뛴 장소만 다시 대상이 된다(AC-014·AC-015).
        if (result.dailyLimitReached) {
            log.warn("{} 일일 한도 도달, 오늘은 여기까지: 확보={}", part, context.getInt(KEY_FETCHED, 0))
            return RepeatStatus.FINISHED
        }

        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_CURSOR = "prefetchCursor"
        const val KEY_FETCHED = "fetched"
        const val KEY_EMPTY = "empty"
        const val KEY_FAILED = "failed"
        const val KEY_SKIPPED = "skipped"
        const val KEY_CALLS = "calls"
    }
}
