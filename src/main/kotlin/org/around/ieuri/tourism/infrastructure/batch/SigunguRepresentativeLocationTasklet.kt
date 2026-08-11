package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.SigunguRepresentativeUpdatePort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 시군구 대표 위치를 다시 고르는 Step 본체(SPEC-0015 · V46).
 *
 * 페이지 루프가 없다 — 선정 전체가 DB 안의 UPDATE 두 문장이라 한 번 호출하고 끝난다.
 *
 * **`cleared`가 0이 아니면 그 지역들이 추천에서 빠졌다는 뜻이다.** 값이 갑자기 커지면 spot
 * 동기화가 덜 끝난 상태에서 이 잡이 돌았을 가능성을 먼저 본다(실행 순서는 잡 구성 참조).
 */
class SigunguRepresentativeLocationTasklet(
    private val updatePort: SigunguRepresentativeUpdatePort,
    private val cellSizeDegrees: Double,
    private val minSpotCount: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = updatePort.refreshRepresentativeLocations(cellSizeDegrees, minSpotCount)

        contribution.incrementWriteCount((result.selected + result.cleared).toLong())
        log.info(
            "sigungu representative location refreshed: selected={} cleared={} cellSize={} minSpotCount={}",
            result.selected, result.cleared, cellSizeDegrees, minSpotCount,
        )
        return RepeatStatus.FINISHED
    }
}
