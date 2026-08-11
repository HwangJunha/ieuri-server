package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.KtoSyncPage
import org.around.ieuri.tourism.application.port.out.persistence.SigunguCodeQueryPort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.LocalDate

/**
 * KTO 빅데이터 한 종을 전량 수집·적재하는 Step 본체(SPEC-0015 R4~R6 · R9).
 *
 * 4종이 이 한 클래스를 공유한다 — 페이지 루프 · 코드 필터 · 집계 로그가 전부 같고 갈리는 것은
 * "무엇을 가져와 어디에 넣는가"뿐이라, 종별로 복제하면 R9 필터가 네 벌이 된다.
 *
 * **알 수 없는 시군구 코드는 건너뛰고 계속한다**(R9). KTO가 코드를 추가하면 우리 정적 테이블이
 * 먼저 갱신되어야 하는데, 그 사이 배치가 통째로 실패하면 정상 데이터까지 못 쌓인다. 건너뛴
 * 건수는 로그로 남긴다. `sigungu`에 FK가 없는 이유가 이것이다(V44 주석).
 *
 * 페이지마다 `RepeatStatus.CONTINUABLE`을 반환하면 Spring Batch가 커밋하므로, 한 페이지가
 * 실패해도 앞 페이지는 남고 재실행이 거기서 이어진다.
 *
 * @param fetchPage (기준일, 페이지 번호) → 한 페이지. 기준일 해석(일/월)은 잡 구성이 정한다.
 * @param sigunguCodeOf 레코드의 시군구 코드 추출. R9 필터가 이 값으로 걸린다.
 * @param upsert 자연키 upsert. 멱등성이 여기서 성립한다(R7).
 */
class TourStatsSyncTasklet<T>(
    private val datasetName: String,
    private val pageSize: Int,
    private val sigunguCodePort: SigunguCodeQueryPort,
    private val fetchPage: (LocalDate, Int) -> KtoSyncPage<T>,
    private val sigunguCodeOf: (T) -> String,
    private val upsert: (List<T>) -> Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 등록된 시군구 코드 스냅샷. Step 시작 시 한 번만 조회한다 — 페이지마다 다시 읽으면 252행
     * 조회가 페이지 수만큼 반복되고, 실행 도중 집합이 바뀌면 페이지별로 다른 기준이 적용된다.
     */
    private var knownCodes: Set<String>? = null

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 기준일은 잡 파라미터에서 온다 — Step 안에서 now()를 부르면 자정을 걸쳐 도는 실행에서
        // 종별로 날짜가 어긋난다(BatchRunDate 참조).
        val runDate = chunkContext.runDate()
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)

        val page = fetchPage(runDate, pageNo)

        val codes = knownCodes ?: sigunguCodePort.findAllCodes().also { knownCodes = it }
        // R9 — 미등록 코드 행만 빼고 나머지는 그대로 적재한다. 예외를 던지지 않는다.
        val (accepted, skipped) = page.records.partition { sigunguCodeOf(it) in codes }
        val written = upsert(accepted)

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalSkipped = executionContext.getInt(KEY_SKIPPED_UNKNOWN_CODE, 0) + skipped.size
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount

        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_SKIPPED_UNKNOWN_CODE, totalSkipped)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        contribution.incrementWriteCount(written.toLong())

        val hasMore = page.hasMore(pageNo, pageSize)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMore) pageNo + 1 else pageNo)

        if (!hasMore) {
            // dropped가 크면 데이터가 아니라 **우리 응답 DTO의 필드명이 틀렸다는 신호다**
            // (응답 스키마가 문서로 확정되지 않았다). skipped가 크면 정적 시군구 테이블이 낡았다.
            log.info(
                "kto sync completed: dataset={} runDate={} written={} skippedUnknownCode={} dropped={}",
                datasetName, runDate, totalWritten, totalSkipped, totalDropped,
            )
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_WRITTEN = "written"

        /** 미등록 시군구 코드로 건너뛴 행 수(R9). 0이 아니면 정적 테이블 갱신이 필요하다. */
        const val KEY_SKIPPED_UNKNOWN_CODE = "skippedUnknownCode"

        /** 필수 필드 누락·파싱 실패로 어댑터가 버린 행 수. */
        const val KEY_DROPPED = "dropped"
    }
}
