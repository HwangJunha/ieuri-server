package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.batch.core.scope.context.ChunkContext
import java.time.LocalDate

/** 실행일 잡 파라미터 이름. [SpotSyncLauncher]가 넣는다. */
const val RUN_DATE_PARAMETER = "runDate"

/**
 * 이 실행의 기준 날짜(SPEC-0007). Step 안에서 `LocalDate.now()`를 부르지 않기 위한 접근자다.
 * - 파라미터가 없으면 오늘로 폴백하지 않고 예외를 던진다.
 */
fun ChunkContext.runDate(): LocalDate {
    val raw = stepContext.stepExecution.jobParameters.getString(RUN_DATE_PARAMETER)
        ?: error("$RUN_DATE_PARAMETER 잡 파라미터가 필요합니다.")
    return LocalDate.parse(raw)
}
