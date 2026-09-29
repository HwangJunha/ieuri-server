package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 시군구 대표 위치를 다시 고르는 Step 본체(SPEC-0015 · V46). UPDATE 두 문장을 한 번 호출한다.
 * - `cleared`가 갑자기 커지면 spot 동기화가 덜 끝난 상태에서 돌았는지 먼저 본다.
 */
class DistrictRepresentativeLocationTasklet(
    private val updatePort: DistrictRepresentativeUpdatePort,
    private val cellSizeDegrees: Double,
    private val minSpotCount: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = updatePort.refreshRepresentativeLocations(cellSizeDegrees, minSpotCount)

        contribution.incrementWriteCount((result.selected + result.cleared).toLong())
        log.info(
            "district representative location refreshed: selected={} cleared={} cellSize={} minSpotCount={}",
            result.selected,
            result.cleared,
            cellSizeDegrees,
            minSpotCount,
        )
        return RepeatStatus.FINISHED
    }
}
