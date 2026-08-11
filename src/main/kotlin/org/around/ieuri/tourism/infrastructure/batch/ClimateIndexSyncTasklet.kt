package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.ClimateIndexSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.ClimateIndexStorePort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 관광기후지수를 전량 수집·적재하는 Step 본체(SPEC-0015 · V49).
 *
 * **[TourStatsSyncTasklet]을 재사용하지 않는다** — 그쪽의 골격은 지역 순회와 미등록 코드
 * 필터(R9)인데 여기는 둘 다 없다. 전국이 한 번에 오고, 코드 매핑은 적재 SQL이 하며 매핑되지
 * 않은 코드도 그대로 적재된다(V49가 FK를 걸지 않은 이유). 페이지 루프만 남는다.
 *
 * **빈 응답은 정상이다**(R8) — 다음 페이지가 없을 뿐 예외가 아니다.
 */
class ClimateIndexSyncTasklet(
    private val syncPort: ClimateIndexSyncPort,
    private val storePort: ClimateIndexStorePort,
    private val days: Int,
    private val pageSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 한 번 호출 = 한 페이지. 매번 [RepeatStatus.CONTINUABLE]로 돌아가 Spring Batch가 커밋하므로,
     * 중간에 실패해도 앞서 적재한 페이지는 남고 재실행이 그 자리에서 이어진다.
     */
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 기준일은 잡 파라미터에서 온다 — Step 안에서 now()를 부르면 자정을 걸쳐 도는 실행에서
        // 페이지마다 조회 기준일이 어긋난다(BatchRunDate 참조).
        val runDate = chunkContext.runDate()
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)

        val page = syncPort.fetchClimateIndices(runDate, days, pageNo, pageSize)
        val written = storePort.upsertClimateIndices(page.records)

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount
        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        contribution.incrementWriteCount(written.toLong())

        if (page.hasMore(pageNo, pageSize)) {
            executionContext.putInt(KEY_NEXT_PAGE, pageNo + 1)
            return RepeatStatus.CONTINUABLE
        }

        // dropped가 크면 데이터가 아니라 **우리 응답 DTO의 필드명이 틀렸다는 신호다.**
        log.info(
            "climate index sync completed: runDate={} days={} written={} dropped={}",
            runDate, days, totalWritten, totalDropped,
        )
        return RepeatStatus.FINISHED
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_WRITTEN = "written"

        /** 필수 필드 누락·파싱 실패로 어댑터가 버린 행 수. */
        const val KEY_DROPPED = "dropped"
    }
}
