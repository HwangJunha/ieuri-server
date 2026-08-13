package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotActivationUseCase
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 비관광 spot 판정 Step 본체(SPEC-0018).
 *
 * **언어 Step과 분리된 별개의 Step이다.** 판정 대상이 언어를 가르지 않는 전량이라 언어마다
 * 돌리면 같은 일을 네 번 하고, 언어 간 전파(R8)는 전 언어 행이 적재된 뒤라야 성립한다.
 * 분리의 더 큰 이유는 트랜잭션이다 — 언어 Step 안에서 돌면 그 Step의 트랜잭션에 합류해,
 * 판정 실패가 그 언어의 적재·정리를 함께 되돌린다(R11이 금지하는 바로 그 동작).
 *
 * **적재·정리 Step 뒤에 와야 한다** — 삭제될 행에 판정을 적용하는 것은 낭비이고, 순서가
 * 반대면 판정 건수와 실제 비활성 행 수가 어긋난다.
 */
class SpotExclusionTasklet(
    private val activationUseCase: SpotActivationUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **예외를 삼키고 Step을 정상 종료시킨다**(R11) — 판정 실패가 앞 Step들의 적재를
     * 무효화해서는 안 된다. 판정은 멱등하므로 다음 실행이 다시 시도해도 부작용이 없다.
     *
     * 이 Step에 트랜잭션 매니저를 주지 않는 것이 실패 격리의 성립 조건이다
     * ([SpotSyncJobConfig.spotExclusionStep] 참조).
     */
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        try {
            val result = activationUseCase.applyExclusions()
            // 전환 건수가 갑자기 크게 늘면 데이터가 아니라 키워드 설정이 잘못됐다는 신호다 —
            // categoryFallback과 같은 성격의 관측값이다.
            log.info(
                "spot exclusion judged: judged={} deactivated={} reactivated={}",
                result.judged, result.deactivated, result.reactivated,
            )
            executionContext.putInt(KEY_JUDGED, result.judged)
            executionContext.putInt(KEY_DEACTIVATED, result.deactivated)
            executionContext.putInt(KEY_REACTIVATED, result.reactivated)
        } catch (e: Exception) {
            // 예외 메시지가 아니라 타입만 남긴다 — 업스트림·드라이버 메시지에 접속 정보가 실릴 수 있다.
            log.error("spot exclusion judgement failed", e)
            executionContext.putString(KEY_JUDGEMENT_FAILED, e.javaClass.simpleName)
        }
        return RepeatStatus.FINISHED
    }

    companion object {
        const val STEP_NAME = "spotExclusionStep"
        const val KEY_JUDGED = "judged"
        const val KEY_DEACTIVATED = "deactivated"
        const val KEY_REACTIVATED = "reactivated"
        const val KEY_JUDGEMENT_FAILED = "judgementFailed"
    }
}
