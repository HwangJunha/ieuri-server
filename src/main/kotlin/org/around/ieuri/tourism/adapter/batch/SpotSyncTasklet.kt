package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.spot.provided.SpotSyncUseCase
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotHarvest
import org.around.ieuri.tourism.domain.spot.SweepBlockedException
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import java.time.LocalDate

/**
 * 한 언어의 관광지 또는 축제 목록을 전량 수집·적재하고, 끝나면 사라진 항목을 정리하는 Step 본체.
 * - 페이지 하나가 유스케이스 호출 하나(= 트랜잭션 하나)다. 실패 후 재실행은 그 페이지에서 이어진다.
 * - 정리 여부는 도메인([SpotHarvest])이 정한다. 막히면 지우지 않고 사유를 실행 이력에 남긴다.
 * - 실행일은 잡 파라미터(`runDate`)에서 읽는다 — `now()`를 부르면 자정을 걸친 실행에서 방금 적재한 축제를 지운다.
 */
class SpotSyncTasklet(
    private val language: DomainLocale,
    private val target: Target,
    private val useCase: SpotSyncUseCase,
    private val pageSize: Int,
) : Tasklet {

    /** 원본 관광지(`areaBasedList2`)인가 축제(`searchFestival2`)인가. */
    enum class Target { SPOT, FESTIVAL }

    private val log = LoggerFactory.getLogger(javaClass)

    /** 이번 실행에서 본 contentId 전체(정리의 생존 집합). 재시작하면 비어 있고, 그래서 재개 실행은 정리하지 않는다. */
    private val collectedIds = HashSet<String>()

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val runDate = chunkContext.runDate()
        val context = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = context.getInt(KEY_NEXT_PAGE, 1)
        // 시작 페이지를 한 번만 기록한다. 1보다 크면 재개 실행이다.
        if (!context.containsKey(KEY_RESUMED_FROM)) context.putInt(KEY_RESUMED_FROM, pageNo)
        // 실행 시각은 첫 페이지에서 한 번 고정한다.
        val syncedAt = context.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = when (target) {
            Target.SPOT -> useCase.syncSpotPage(language, pageNo, pageSize, syncedAt, runDate)
            Target.FESTIVAL -> useCase.syncFestivalPage(language, pageNo, pageSize, syncedAt, runDate)
        }
        collectedIds += page.contentIds

        context.putString(KEY_SYNCED_AT, syncedAt.toString())
        context.add(KEY_WRITTEN, page.written)
        context.add(KEY_DROPPED, page.dropped)
        context.add(KEY_CATEGORY_FALLBACK, page.categoryFallback)
        context.add(KEY_EXCLUDED, page.excluded)
        contribution.incrementWriteCount(page.written.toLong())
        context.putInt(KEY_NEXT_PAGE, if (page.hasMore) pageNo + 1 else pageNo)
        if (page.hasMore) return RepeatStatus.CONTINUABLE

        // 폴백 건수가 크면 category_external_mapping 행 누락, 제외 건수가 크면 키워드 설정 오류의 신호다(SPEC-0018).
        log.info(
            "{} sync completed: language={} written={} dropped={} categoryFallback={} excluded={}",
            target,
            language,
            context.getInt(KEY_WRITTEN),
            context.getInt(KEY_DROPPED),
            context.getInt(KEY_CATEGORY_FALLBACK),
            context.getInt(KEY_EXCLUDED),
        )
        context.putInt(KEY_COLLECTED, collectedIds.size)
        sweep(context, SpotHarvest(collectedIds.toSet(), resumed = context.getInt(KEY_RESUMED_FROM, 1) > 1), runDate)
        return RepeatStatus.FINISHED
    }

    private fun sweep(context: ExecutionContext, harvest: SpotHarvest, runDate: LocalDate) {
        try {
            when (target) {
                Target.SPOT -> {
                    val deleted = useCase.sweepSpots(language, harvest)
                    log.info("spot cleanup: language={} deleted={} surviving={}", language, deleted, harvest.contentIds.size)
                    context.putInt(KEY_DELETED, deleted)
                }

                Target.FESTIVAL -> {
                    val result = useCase.sweepFestivals(language, harvest, runDate)
                    log.info("festival cleanup: language={} ended={} missing={} surviving={}", language, result.ended, result.missing, harvest.contentIds.size)
                    context.putInt(KEY_DELETED, result.ended + result.missing)
                    context.putInt(KEY_DELETED_ENDED, result.ended)
                }
            }
        } catch (e: SweepBlockedException) {
            log.warn("{} cleanup skipped: language={} reason={}", target, language, e.reason)
            context.putString(KEY_CLEANUP_SKIPPED, e.reason)
        }
    }

    private fun ExecutionContext.add(key: String, value: Int) = putInt(key, getInt(key, 0) + value)

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_RESUMED_FROM = "resumedFrom"
        const val KEY_CLEANUP_SKIPPED = "cleanupSkipped"
        const val KEY_DELETED = "deleted"
        const val KEY_DELETED_ENDED = "deletedEnded"
        const val KEY_SYNCED_AT = "syncedAt"
        const val KEY_WRITTEN = "written"
        const val KEY_DROPPED = "dropped"
        const val KEY_CATEGORY_FALLBACK = "categoryFallback"
        const val KEY_EXCLUDED = "excluded"
        const val KEY_COLLECTED = "collected"
    }
}
