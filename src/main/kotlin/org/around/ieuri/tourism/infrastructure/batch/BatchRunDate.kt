package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.batch.core.scope.context.ChunkContext
import java.time.LocalDate

/** 실행일 잡 파라미터 이름. [SpotSyncLauncher]가 넣고 축제 Step이 읽는다. */
const val RUN_DATE_PARAMETER = "runDate"

/**
 * 이 실행의 기준 날짜(SPEC-0007). **`LocalDate.now()`를 Step 안에서 부르지 않기 위한 접근자다.**
 *
 * [SpotSyncLauncher]가 이미 `runDate`를 식별 잡 파라미터로 넣고 있다(SPEC-0004 AC-6: 같은 날짜 =
 * 같은 JobInstance = 중복 실행 불가). 축제 수집 조건(AC-8)·종료 판정(R-8)·재삽입 제외 집합(R-9)이
 * 전부 이 하나의 값에서 나와야 자정을 걸쳐 도는 실행에서 단계마다 날짜가 어긋나지 않는다.
 *
 * 파라미터가 없으면 예외를 던진다 — 조용히 오늘로 폴백하면 그 어긋남이 다시 생긴다.
 */
fun ChunkContext.runDate(): LocalDate {
    val raw = stepContext.stepExecution.jobParameters.getString(RUN_DATE_PARAMETER)
        ?: error("$RUN_DATE_PARAMETER 잡 파라미터가 필요합니다.")
    return LocalDate.parse(raw)
}
