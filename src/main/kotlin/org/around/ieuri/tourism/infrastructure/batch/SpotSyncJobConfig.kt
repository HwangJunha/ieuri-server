package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotActivationUseCase
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 관광지·축제 동기화 잡 구성.
 *
 * ```
 * spotSyncJob
 * ├── syncLanguageStep     ×4 (KO → EN → JA → ZH, 순차)
 * ├── festivalSyncStep     ×4 (KO → EN → JA → ZH, 순차)
 * └── spotExclusionStep    ×1 (언어 무관 전량 판정)
 * ```
 *
 * **축제 Step이 관광지 Step 뒤에 와야 한다** — `spot_festival`이 `spot`에 FK를 걸므로 부모 행이
 * 먼저 있어야 하고, 순서가 뒤집히면 첫 실행에서 축제가 전부 건너뛰어진다.
 *
 * **비관광 판정 Step이 맨 뒤다** — 적재·정리가 끝난 집합을 대상으로 해야 하고, 언어를 가르지
 * 않는 전량이 대상이라 하나뿐이다([spotExclusionStep]).
 *
 * **순차 실행이어야 한다** — serviceKey가 4개 언어 공용 단일 키라 병렬화하면 호출 한도를 4배
 * 속도로 소모한다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val properties: SpotSyncProperties,
    private val festivalSyncPort: FestivalSyncPort,
    private val festivalStorePort: FestivalStorePort,
    private val festivalProperties: FestivalSyncProperties,
    private val activationUseCase: SpotActivationUseCase,
) {

    @Bean
    fun spotSyncJob(): Job {
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        // 관광지 4개 언어를 모두 마친 뒤 축제 4개 언어가 오고, 비관광 판정이 마지막이다 —
        // 부모 행이 먼저 있어야 하고, 판정은 적재·정리가 끝난 집합을 대상으로 해야 한다.
        val steps = order.map(::syncStep) + order.map(::festivalStep) + spotExclusionStep()
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
            .build()
    }

    /**
     * 언어 1개를 전량 수집하는 Step. `allowStartIfComplete(false)`(기본값)라, 재실행 시 이미 완료된 언어
     * Step은 건너뛰고 실패한 언어부터 재개한다.
     */
    private fun syncStep(language: DomainLocale): Step =
        StepBuilder("syncLanguageStep-${language.name}", jobRepository)
            .tasklet(
                SpotSyncTasklet(
                    language = language,
                    syncPort = syncPort,
                    storePort = storePort,
                    pageSize = properties.pageSize,
                    // 조회를 Step 실행 시점으로 미룬다 — 람다를 벗기면 부팅 때 잡은 낡은 집합으로
                    // 걸러 종료 축제가 되살아난다.
                    endedFestivalIds = { runDate -> festivalStorePort.findEndedContentIds(language, runDate) },
                ),
                transactionManager,
            )
            .build()

    /**
     * 비관광 spot 판정 Step(SPEC-0018). **언어를 가르지 않는 전량이 대상이라 Step이 하나다.**
     *
     * **`ResourcelessTransactionManager`를 준다** — 이것이 실패 격리(R11)의 성립 조건이다.
     * 공용 `transactionManager`를 주면 Step 트랜잭션이 열리고, 판정의 `@Transactional`이
     * 거기 합류해 실패 시 그 트랜잭션이 rollback-only로 표시된다. 그러면 Tasklet이 예외를
     * 삼켜도 커밋 단계에서 `UnexpectedRollbackException`으로 Step이 깨진다.
     *
     * 자원 없는 매니저를 주면 Step은 트랜잭션을 열지 않고, 판정의 `@Transactional`이 스스로
     * 트랜잭션을 시작·커밋한다. 실패는 그 안에서 닫히고 Tasklet의 catch가 진짜로 흡수한다.
     */
    private fun spotExclusionStep(): Step =
        StepBuilder(SpotExclusionTasklet.STEP_NAME, jobRepository)
            .tasklet(SpotExclusionTasklet(activationUseCase), ResourcelessTransactionManager())
            .build()

    /**
     * 언어 1개의 축제 정보를 수집·적재하고 정리하는 Step. Step이 분리되어 있어 한 언어의 실패가
     * 앞 언어의 커밋을 되돌리지 않는다.
     */
    private fun festivalStep(language: DomainLocale): Step =
        StepBuilder("festivalSyncStep-${language.name}", jobRepository)
            .tasklet(
                FestivalSyncTasklet(
                    language = language,
                    syncPort = festivalSyncPort,
                    storePort = festivalStorePort,
                    pageSize = festivalProperties.pageSize,
                ),
                transactionManager,
            )
            .build()

    companion object {
        const val JOB_NAME = "spotSyncJob"
    }
}
