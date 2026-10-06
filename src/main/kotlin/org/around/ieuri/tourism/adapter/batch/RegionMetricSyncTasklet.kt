package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.shared.required.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.shared.required.DistrictRegion
import org.around.ieuri.tourism.application.shared.required.RegionMetricSyncPage
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.LocalDate

/**
 * 한 페이지를 가져올 때 잡 구성에 넘겨주는 실행 문맥.
 *
 * @param runDate 잡 파라미터에서 온 기준일.
 * @param executionContext 실행당 한 번만 정할 값(월 지표의 기준월 등)을 남기면 재시작 후에도 유지된다.
 */
data class SyncContext(
    val runDate: LocalDate,
    val executionContext: ExecutionContext,
)

/**
 * KTO 빅데이터 한 종을 전량 수집·적재하는 Step 본체(SPEC-0015 R4~R6·R9). 4종이 공유한다.
 * - 순회 단위는 [targetsOf]가 정한다: 방문자수 [NO_REGION], 강도 2종 시도 17개, 집중률 시군구 252개.
 * - 알 수 없는 시군구 코드(시도 집계 행 포함)는 건너뛰고 건수만 로그로 남긴다(R9).
 * - 빈 응답은 예외가 아니라 다음 대상으로 넘어간다(R8).
 *
 * @param R 순회 단위(시군구 [DistrictRegion] · 시도 코드 [String] · 단일 [NO_REGION]).
 * @param fetchPage (수집 문맥, 순회 단위, 페이지 번호) → 한 페이지.
 * @param upsert 자연키 upsert. 멱등성이 여기서 성립한다(R7).
 */
class RegionMetricSyncTasklet<T, R : Any>(
    private val datasetName: String,
    private val pageSize: Int,
    private val districtCodePort: DistrictCodeQueryPort,
    private val fetchPage: (SyncContext, R, Int) -> RegionMetricSyncPage<T>,
    private val districtCodeOf: (T) -> String,
    private val upsert: (List<T>) -> Int,
    private val targetsOf: (DistrictCodeQueryPort) -> List<R>,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 등록된 시군구 코드 스냅샷. Step 시작 시 한 번만 조회한다. */
    private var knownCodes: Set<String>? = null

    /** 순회 대상 스냅샷. 한 번만 읽는다 — 순서가 바뀌면 재개 인덱스가 어긋난다. */
    private var targets: List<R>? = null

    /**
     * 한 번 호출 = 한 대상의 한 페이지. 매번 CONTINUABLE로 커밋하므로 실패 후 재실행이 그 자리에서 이어진다.
     */
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 기준일은 잡 파라미터에서 온다(BatchRunDate 참조).
        val runDate = chunkContext.runDate()
        val executionContext = chunkContext.stepContext.stepExecution.executionContext

        val loopTargets = targets ?: targetsOf(districtCodePort).also { targets = it }
        if (loopTargets.isEmpty()) {
            log.warn("kto sync: dataset={} 순회 대상이 없습니다 — district 테이블이 비어 있습니다.", datasetName)
            return RepeatStatus.FINISHED
        }

        val regionIndex = executionContext.getInt(KEY_REGION_INDEX, 0)
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        val target = loopTargets[regionIndex]

        val page = fetchPage(SyncContext(runDate, executionContext), target, pageNo)

        val codes = knownCodes ?: districtCodePort.findAllCodes().also { knownCodes = it }
        // R9 — 미등록 코드 행만 빼고 적재한다. 예외를 던지지 않는다.
        val (accepted, skipped) = page.records.partition { districtCodeOf(it) in codes }
        val written = upsert(accepted)

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalSkipped = executionContext.getInt(KEY_SKIPPED_UNKNOWN_CODE, 0) + skipped.size
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount

        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_SKIPPED_UNKNOWN_CODE, totalSkipped)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        contribution.incrementWriteCount(written.toLong())

        // 페이지가 남았으면 같은 대상의 다음 페이지, 아니면 다음 대상의 1페이지(빈 응답 포함, R8).
        val hasMorePages = page.hasMore(pageNo, pageSize)
        val nextRegionIndex = if (hasMorePages) regionIndex else regionIndex + 1
        executionContext.putInt(KEY_REGION_INDEX, nextRegionIndex)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMorePages) pageNo + 1 else 1)

        val finished = nextRegionIndex >= loopTargets.size
        if (!finished) {
            logProgress(nextRegionIndex, loopTargets.size, totalWritten)
            return RepeatStatus.CONTINUABLE
        }

        // dropped가 크면 응답 DTO의 필드명이 틀렸다는 신호, skipped가 크면 정적 시군구 테이블이 낡았다는 신호다.
        log.info(
            "kto sync completed: dataset={} runDate={} regions={} written={} skippedUnknownCode={} dropped={}",
            datasetName,
            runDate,
            loopTargets.size,
            totalWritten,
            totalSkipped,
            totalDropped,
        )
        return RepeatStatus.FINISHED
    }

    /** 지역 루프의 진척을 주기적으로 남긴다. */
    private fun logProgress(nextRegionIndex: Int, total: Int, written: Int) {
        if (total > 1 && nextRegionIndex % PROGRESS_LOG_INTERVAL == 0) {
            log.info(
                "kto sync progress: dataset={} region={}/{} written={}",
                datasetName,
                nextRegionIndex,
                total,
                written,
            )
        }
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_WRITTEN = "written"

        /** 다음에 처리할 대상의 인덱스. */
        const val KEY_REGION_INDEX = "regionIndex"

        /** 미등록 시군구 코드로 건너뛴 행 수(R9). 0이 아니면 정적 테이블 갱신이 필요하다. */
        const val KEY_SKIPPED_UNKNOWN_CODE = "skippedUnknownCode"

        /** 필수 필드 누락·파싱 실패로 어댑터가 버린 행 수. */
        const val KEY_DROPPED = "dropped"

        private const val PROGRESS_LOG_INTERVAL = 20

        /** 지역 필터가 없는 종(방문자수)의 단일 순회 단위. 업스트림으로 나가지 않는다. */
        val NO_REGION: List<Unit> = listOf(Unit)
    }
}
