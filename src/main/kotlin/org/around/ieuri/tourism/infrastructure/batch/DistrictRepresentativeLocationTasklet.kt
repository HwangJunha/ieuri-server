package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 시군구 대표 위치를 다시 고르고 카테고리 구성비를 집계하는 Step 본체(SPEC-0015 · V46 · V50).
 *
 * 페이지 루프가 없다 — 전부 DB 안의 UPDATE 문장이라 한 번 호출하고 끝난다.
 *
 * **둘을 한 Step에 두는 이유**: 같은 `spot`을 같은 공간 조인으로 읽으므로 같은 시점의 데이터로
 * 계산되어야 한다. Step을 나누면 사이에 spot이 바뀌어 둘이 어긋날 수 있다.
 *
 * **`cleared`가 0이 아니면 그 지역들이 추천에서 빠졌다는 뜻이다.** 값이 갑자기 커지면 spot
 * 동기화가 덜 끝난 상태에서 이 잡이 돌았을 가능성을 먼저 본다(실행 순서는 잡 구성 참조).
 */
class DistrictRepresentativeLocationTasklet(
    private val updatePort: DistrictRepresentativeUpdatePort,
    private val cellSizeDegrees: Double,
    private val minSpotCount: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = updatePort.refreshRepresentativeLocations(cellSizeDegrees, minSpotCount)
        // 대표 위치 다음에 온다 — 순서에 의존성은 없지만, 앞이 실패하면 집계도 돌지 않아야
        // 둘의 기준 시점이 어긋난 상태로 남지 않는다.
        val categoryMixUpdated = updatePort.refreshCategoryMix()

        contribution.incrementWriteCount((result.selected + result.cleared + categoryMixUpdated).toLong())
        log.info(
            "district refreshed: selected={} cleared={} categoryMix={} cellSize={} minSpotCount={}",
            result.selected, result.cleared, categoryMixUpdated, cellSizeDegrees, minSpotCount,
        )
        return RepeatStatus.FINISHED
    }
}
