package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.application.port.out.persistence.SigunguCodeQueryPort
import org.around.ieuri.tourism.application.port.out.persistence.SigunguRegion
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.time.LocalDate

/**
 * 한 페이지를 가져올 때 잡 구성에 넘겨주는 실행 문맥.
 *
 * @param runDate 잡 파라미터에서 온 기준일. Step 안에서 now()를 부르지 않는 이유는 [runDate] 참조.
 * @param executionContext Step의 ExecutionContext. **실행당 한 번만 정해야 하는 값**(월 지표의
 *   기준월 등)을 여기에 남기면 재시작 후에도 다시 정하지 않는다.
 */
data class SyncContext(
    val runDate: LocalDate,
    val executionContext: ExecutionContext,
)

/**
 * KTO 빅데이터 한 종을 전량 수집·적재하는 Step 본체(SPEC-0015 R4~R6 · R9).
 *
 * 4종이 이 한 클래스를 공유한다 — 지역·페이지 루프 · 코드 필터 · 집계 로그가 전부 같고 갈리는
 * 것은 "무엇을 가져와 어디에 넣는가"뿐이라, 종별로 복제하면 R9 필터가 네 벌이 된다.
 *
 * **순회 단위가 세 갈래다**(2026-08-11 실측). 방문자수는 지역 필터가 없어 전 지역이 한 번에
 * 오고([NO_REGION]), 강도 2종은 `areaCd`만으로 시도 전체가 와서 시도 17개를 돌며, 집중률만
 * `signguCd`가 필수라 시군구 252개를 돈다. 대상은 [targetsOf]가 정하고 루프는 하나다.
 *
 * **알 수 없는 시군구 코드는 건너뛰고 계속한다**(R9). 건너뛴 건수는 로그로 남긴다. 시도 단위
 * 응답에 섞여 오는 시도 집계 행도 여기서 걸린다 — 2자리 시도 코드는 5자리 시군구 코드 집합에
 * 결코 들어 있지 않기 때문이다.
 *
 * **빈 응답은 정상이다**(R8) — 다음 대상으로 넘어갈 뿐 예외가 아니다.
 *
 * @param T 적재 레코드 타입.
 * @param R 순회 단위(시군구 [SigunguRegion] · 시도 코드 [String] · 단일 [NO_REGION]).
 * @param fetchPage (수집 문맥, 순회 단위, 페이지 번호) → 한 페이지. 기준일 해석(일/월)과
 *   기준월 탐색은 잡 구성이 정하며, 실행 간 기억이 필요하면 [SyncContext.executionContext]에 쓴다.
 * @param sigunguCodeOf 레코드의 시군구 코드 추출. R9 필터가 이 값으로 걸린다.
 * @param upsert 자연키 upsert. 멱등성이 여기서 성립한다(R7).
 * @param targetsOf 순회 대상. 기본은 지역 순회 없음([NO_REGION] 한 건).
 */
class TourStatsSyncTasklet<T, R : Any>(
    private val datasetName: String,
    private val pageSize: Int,
    private val sigunguCodePort: SigunguCodeQueryPort,
    private val fetchPage: (SyncContext, R, Int) -> TourStatsSyncPage<T>,
    private val sigunguCodeOf: (T) -> String,
    private val upsert: (List<T>) -> Int,
    private val targetsOf: (SigunguCodeQueryPort) -> List<R>,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 등록된 시군구 코드 스냅샷. Step 시작 시 한 번만 조회한다 — 페이지마다 다시 읽으면 252행
     * 조회가 페이지 수만큼 반복되고, 실행 도중 집합이 바뀌면 페이지별로 다른 기준이 적용된다.
     */
    private var knownCodes: Set<String>? = null

    /** 순회 대상 스냅샷. 같은 이유로 한 번만 읽는다 — 순서가 바뀌면 재개 인덱스가 어긋난다. */
    private var targets: List<R>? = null

    /**
     * 한 번 호출 = 한 대상의 한 페이지. 매번 [RepeatStatus.CONTINUABLE]로 돌아가 Spring Batch가
     * 커밋하므로, 중간에 실패해도 앞서 적재한 대상·페이지는 남고 재실행이 그 자리에서 이어진다.
     */
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 기준일은 잡 파라미터에서 온다 — Step 안에서 now()를 부르면 자정을 걸쳐 도는 실행에서
        // 종별로 날짜가 어긋난다(BatchRunDate 참조).
        val runDate = chunkContext.runDate()
        val executionContext = chunkContext.stepContext.stepExecution.executionContext

        val loopTargets = targets ?: targetsOf(sigunguCodePort).also { targets = it }
        if (loopTargets.isEmpty()) {
            log.warn("kto sync: dataset={} 순회 대상이 없습니다 — sigungu 테이블이 비어 있습니다.", datasetName)
            return RepeatStatus.FINISHED
        }

        val regionIndex = executionContext.getInt(KEY_REGION_INDEX, 0)
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        val target = loopTargets[regionIndex]

        val page = fetchPage(SyncContext(runDate, executionContext), target, pageNo)

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

        // 이 대상에 페이지가 남았으면 같은 대상의 다음 페이지, 아니면 다음 대상의 1페이지.
        // 빈 응답이면 hasMore가 false라 자연히 다음 대상으로 넘어간다(R8).
        val hasMorePages = page.hasMore(pageNo, pageSize)
        val nextRegionIndex = if (hasMorePages) regionIndex else regionIndex + 1
        executionContext.putInt(KEY_REGION_INDEX, nextRegionIndex)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMorePages) pageNo + 1 else 1)

        val finished = nextRegionIndex >= loopTargets.size
        if (!finished) {
            logProgress(nextRegionIndex, loopTargets.size, totalWritten)
            return RepeatStatus.CONTINUABLE
        }

        // dropped가 크면 데이터가 아니라 **우리 응답 DTO의 필드명이 틀렸다는 신호다**
        // (응답 스키마가 문서로 확정되지 않았다). skipped가 크면 정적 시군구 테이블이 낡았다.
        log.info(
            "kto sync completed: dataset={} runDate={} regions={} written={} skippedUnknownCode={} dropped={}",
            datasetName, runDate, loopTargets.size, totalWritten, totalSkipped, totalDropped,
        )
        return RepeatStatus.FINISHED
    }

    /** 252지역 루프가 멈춘 것처럼 보이지 않도록 주기적으로 진척을 남긴다. */
    private fun logProgress(nextRegionIndex: Int, total: Int, written: Int) {
        if (total > 1 && nextRegionIndex % PROGRESS_LOG_INTERVAL == 0) {
            log.info(
                "kto sync progress: dataset={} region={}/{} written={}",
                datasetName, nextRegionIndex, total, written,
            )
        }
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_WRITTEN = "written"

        /** 다음에 처리할 대상의 인덱스. 지역 순회가 없는 종은 0에서 1로만 움직인다. */
        const val KEY_REGION_INDEX = "regionIndex"

        /** 미등록 시군구 코드로 건너뛴 행 수(R9). 0이 아니면 정적 테이블 갱신이 필요하다. */
        const val KEY_SKIPPED_UNKNOWN_CODE = "skippedUnknownCode"

        /** 필수 필드 누락·파싱 실패로 어댑터가 버린 행 수. */
        const val KEY_DROPPED = "dropped"

        private const val PROGRESS_LOG_INTERVAL = 20

        /**
         * 지역 필터가 없는 종(방문자수)의 단일 순회 단위. 값이 Unit인 것은 업스트림으로 나가지
         * 않기 때문이다 — 이 값을 쿼리 파라미터로 넘기는 fetchPage는 없다.
         */
        val NO_REGION: List<Unit> = listOf(Unit)
    }
}
