package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPort
import org.around.ieuri.tourism.application.port.out.persistence.PetFriendlyStorePort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 반려동물 동반 가능 관광지를 전량 수집·적재하는 Step 본체(SPEC-0015 · V48).
 *
 * 페이지 단위로 루프를 돌며 다음 페이지 번호를 ExecutionContext에 남긴다.
 * `RepeatStatus.CONTINUABLE` 반환마다 Spring Batch가 커밋하므로, 한 페이지가 실패해도 앞
 * 페이지는 남고 재실행이 거기서 이어진다.
 *
 * **정리 단계가 없다** — 목록에서 빠진 건은 다음 실행까지 남는다(사유는 [PetFriendlyStorePort]).
 * 그래서 [SpotSyncTasklet]의 급감 게이트에 해당하는 방어도 필요 없다.
 *
 * **빈 응답은 정상이다**(R8) — `hasMore`가 false가 되어 그대로 종료한다.
 */
class PetFriendlySyncTasklet(
    private val syncPort: PetFriendlySyncPort,
    private val storePort: PetFriendlyStorePort,
    private val pageSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)

        val page = syncPort.fetchPage(pageNo, pageSize)
        val written = storePort.upsertAll(page.records)

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount
        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        contribution.incrementWriteCount(written.toLong())

        val hasMore = page.hasMore(pageNo, pageSize)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMore) pageNo + 1 else pageNo)

        if (!hasMore) {
            // dropped가 크면 데이터가 아니라 **응답 필드명이 바뀌었다는 신호다**.
            log.info(
                "pet friendly sync completed: written={} dropped={} totalCount={}",
                totalWritten, totalDropped, page.totalCount,
            )
            return RepeatStatus.FINISHED
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_WRITTEN = "written"

        /** contentId 결손으로 버린 행 수. 0이 아니면 응답 스키마 변경을 의심한다. */
        const val KEY_DROPPED = "dropped"
    }
}
