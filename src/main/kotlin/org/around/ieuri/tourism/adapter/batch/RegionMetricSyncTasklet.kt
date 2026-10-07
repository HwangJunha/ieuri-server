package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.region.provided.RegionMetricSyncUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricDataset
import org.around.ieuri.tourism.application.region.provided.dto.RegionMetricTarget
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.YearMonth

/**
 * KTO 빅데이터 한 종을 전량 동기화하는 Step 본체(SPEC-0015). 한 번 호출 = 한 대상의 한 페이지다.
 * - 진행 위치(대상 인덱스·페이지·정해진 기준월)를 실행 문맥에 남겨 재실행이 그 자리에서 이어진다 — 일 호출 한도 때문이다.
 * - 빈 응답은 예외가 아니라 다음 대상으로 넘어간다(R8).
 */
class RegionMetricSyncTasklet(
    private val dataset: RegionMetricDataset,
    private val useCase: RegionMetricSyncUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 순회 대상 스냅샷. 한 번만 읽는다 — 순서가 바뀌면 재개 인덱스가 어긋난다. */
    private var targets: List<RegionMetricTarget>? = null

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val runDate = chunkContext.runDate()
        val context = chunkContext.stepContext.stepExecution.executionContext
        val loopTargets = targets ?: useCase.targets(dataset).also { targets = it }
        if (loopTargets.isEmpty()) {
            log.warn("kto sync: dataset={} 순회 대상이 없습니다 — district 테이블이 비어 있습니다.", dataset)
            return RepeatStatus.FINISHED
        }

        val targetIndex = context.getInt(KEY_TARGET_INDEX, 0)
        val pageNo = context.getInt(KEY_NEXT_PAGE, 1)
        val baseMonth = context.getString(KEY_BASE_MONTH, "").takeIf { it.isNotEmpty() }?.let(YearMonth::parse)

        val page = useCase.syncPage(dataset, loopTargets[targetIndex], pageNo, runDate, baseMonth)

        page.baseMonth?.let { context.putString(KEY_BASE_MONTH, it.toString()) }
        context.add(KEY_WRITTEN, page.written)
        context.add(KEY_SKIPPED_UNKNOWN_CODE, page.skippedUnknownCode)
        context.add(KEY_DROPPED, page.dropped)
        contribution.incrementWriteCount(page.written.toLong())

        // 페이지가 남았으면 같은 대상의 다음 페이지, 아니면 다음 대상의 1페이지.
        val nextTargetIndex = if (page.hasMore) targetIndex else targetIndex + 1
        context.putInt(KEY_TARGET_INDEX, nextTargetIndex)
        context.putInt(KEY_NEXT_PAGE, if (page.hasMore) pageNo + 1 else 1)
        if (nextTargetIndex < loopTargets.size) return RepeatStatus.CONTINUABLE

        // dropped가 크면 응답 필드명이, skipped가 크면 정적 시군구 테이블이 낡았다는 신호다.
        log.info(
            "kto sync completed: dataset={} runDate={} targets={} written={} skippedUnknownCode={} dropped={}",
            dataset,
            runDate,
            loopTargets.size,
            context.getInt(KEY_WRITTEN),
            context.getInt(KEY_SKIPPED_UNKNOWN_CODE),
            context.getInt(KEY_DROPPED),
        )
        return RepeatStatus.FINISHED
    }

    private fun ExecutionContext.add(key: String, value: Int) = putInt(key, getInt(key, 0) + value)

    companion object {
        const val KEY_TARGET_INDEX = "regionIndex"
        const val KEY_NEXT_PAGE = "nextPage"

        /** 월 지표의 정해진 기준월(`uuuu-MM`). 재시작해도 다시 찾지 않는다. */
        const val KEY_BASE_MONTH = "baseMonth"
        const val KEY_WRITTEN = "written"
        const val KEY_SKIPPED_UNKNOWN_CODE = "skippedUnknownCode"
        const val KEY_DROPPED = "dropped"
    }
}
