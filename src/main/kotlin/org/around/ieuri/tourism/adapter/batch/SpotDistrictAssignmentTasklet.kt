package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCurationUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 카탈로그를 시군구에 귀속시키는 Step 본체(SPEC-0030 R11 · V75).
 * - `unassigned`는 0이 되지 않는다(바다·경계 밖 좌표). 갑자기 커지면 경계 데이터나 좌표 적재를 의심한다.
 */
class SpotDistrictAssignmentTasklet(
    private val curationUseCase: SpotCatalogCurationUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = curationUseCase.assignDistricts()

        contribution.incrementWriteCount(result.assigned.toLong())
        log.info("spot district assigned: assigned={} unassigned={}", result.assigned, result.unassigned)
        return RepeatStatus.FINISHED
    }
}
