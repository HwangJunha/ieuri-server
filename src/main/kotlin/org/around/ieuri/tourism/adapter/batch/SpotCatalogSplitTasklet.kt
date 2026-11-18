package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCurationUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
/**
 * 잘못 접힌 카탈로그를 가르는 Step 본체(SPEC-0029). 배정 Step 뒤에 한 번만 돈다.
 * - 가른 것이 있으면 노출 갱신을 다시 부른다 — 안 부르면 구독자의 사본이 낡는다.
 */
class SpotCatalogSplitTasklet(
    private val useCase: SpotCatalogCurationUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = useCase.splitMismatchedCatalogs()
        contribution.incrementWriteCount(result.spotsMoved.toLong())

        if (result.catalogsCreated > 0) useCase.refreshCatalogVisibility()

        log.info(
            "catalog split finished: split={} created={} moved={}",
            result.catalogsSplit,
            result.catalogsCreated,
            result.spotsMoved,
        )
        return RepeatStatus.FINISHED
    }
}
