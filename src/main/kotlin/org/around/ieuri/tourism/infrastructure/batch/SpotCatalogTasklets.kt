package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogSplitUseCase
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 카탈로그 배정 Step 본체(SPEC-0019 §3). 한 회차에 상한만큼 처리하고 남으면 CONTINUABLE로 다시 돈다.
 * - KO를 전량 소진한 뒤 EN·JA·ZH로 넘어간다 — 뒤집히면 같은 장소가 두 카탈로그로 갈린다.
 */
class SpotCatalogAssignmentTasklet(
    private val useCase: SpotCatalogUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext

        // 진행 중인 언어를 남겨 재개 시 같은 지점에서 이어진다.
        val languageIndex = executionContext.getInt(KEY_LANGUAGE_INDEX, 0)
        if (languageIndex >= ASSIGNMENT_ORDER.size) {
            // 배정을 다 마친 뒤에야 고아를 확정할 수 있다.
            useCase.refreshCatalogVisibility()
            log.info(
                "catalog assignment finished: joined={} created={} skipped={}",
                executionContext.getInt(KEY_JOINED, 0),
                executionContext.getInt(KEY_CREATED, 0),
                executionContext.getInt(KEY_SKIPPED, 0),
            )
            return RepeatStatus.FINISHED
        }

        val language = ASSIGNMENT_ORDER[languageIndex]
        // 키셋 커서 — 건너뛴 행은 catalog_id가 null로 남아, 커서 없이 재조회하면 같은 행을 무한히 읽는다.
        val cursor = executionContext.getLong(KEY_CURSOR, 0L)
        val result = useCase.assignCatalogs(language, cursor)

        executionContext.putInt(KEY_JOINED, executionContext.getInt(KEY_JOINED, 0) + result.joined)
        executionContext.putInt(KEY_CREATED, executionContext.getInt(KEY_CREATED, 0) + result.catalogsCreated)
        executionContext.putInt(KEY_SKIPPED, executionContext.getInt(KEY_SKIPPED, 0) + result.skipped)
        executionContext.putLong(KEY_CURSOR, result.lastSpotId)
        contribution.incrementWriteCount(result.assigned.toLong())

        // `assigned`가 아니라 `processed`로 판정한다 — 전부 skip인 회차를 소진으로 오판하지 않는다.
        if (result.processed == 0) {
            log.info(
                "catalog assignment: language={} exhausted (skipped={})",
                language,
                executionContext.getInt(KEY_SKIPPED, 0),
            )
            executionContext.putInt(KEY_LANGUAGE_INDEX, languageIndex + 1)
            // 다음 언어는 처음부터 읽는다.
            executionContext.putLong(KEY_CURSOR, 0L)
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_JOINED = "joined"
        const val KEY_CREATED = "catalogsCreated"
        const val KEY_LANGUAGE_INDEX = "languageIndex"
        const val KEY_SKIPPED = "skipped"

        /** 이 언어에서 마지막으로 읽은 spot id. 언어가 바뀌면 0으로 되돌린다. */
        const val KEY_CURSOR = "cursor"

        /** KO가 먼저다. 나머지 순서는 무관하다. */
        val ASSIGNMENT_ORDER = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}

/**
 * 잘못 접힌 카탈로그를 가르는 Step 본체(SPEC-0029). 배정 Step 뒤에 한 번만 돈다.
 * - 가른 것이 있으면 노출 갱신을 다시 부른다 — 안 부르면 구독자의 사본이 낡는다.
 */
class SpotCatalogSplitTasklet(
    private val splitUseCase: SpotCatalogSplitUseCase,
    private val catalogUseCase: SpotCatalogUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = splitUseCase.splitMismatchedCatalogs()
        contribution.incrementWriteCount(result.spotsMoved.toLong())

        if (result.catalogsCreated > 0) catalogUseCase.refreshCatalogVisibility()

        log.info(
            "catalog split finished: split={} created={} moved={}",
            result.catalogsSplit,
            result.catalogsCreated,
            result.spotsMoved,
        )
        return RepeatStatus.FINISHED
    }
}
