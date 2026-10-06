package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 카탈로그를 시군구에 귀속시키는 Step 본체(SPEC-0030 R11 · V75). UPDATE 한 문장을 한 번 호출한다.
 * - `unassigned`는 0이 되지 않는다(바다·경계 밖 좌표). 갑자기 커지면 경계 데이터나 좌표 적재를 의심한다.
 */
class SpotDistrictAssignmentTasklet(
    private val catalogRepository: SpotCatalogRepository,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val assigned = catalogRepository.assignUnassignedDistricts()
        val unassigned = catalogRepository.countDistrictUnassigned()

        contribution.incrementWriteCount(assigned.toLong())
        log.info("spot district assigned: assigned={} unassigned={}", assigned, unassigned)
        return RepeatStatus.FINISHED
    }
}
