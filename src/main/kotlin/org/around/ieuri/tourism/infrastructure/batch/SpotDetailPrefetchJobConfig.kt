package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 관광지 상세 사전 수집 잡(SPEC-0037). `spotSyncJob`과 **별개 잡**이다 — 갈래·언어당 수만 회의
 * 외부 호출이라 한도로 중단될 수 있고, 그것이 목록 동기화를 붙들면 목록 자체가 매일 최신화되지
 * 않는다(`spotEmbeddingJob`과 같은 사유).
 *
 * 목록 수집이 끝난 뒤에 돌아야 한다 — 새로 들어온 장소의 상세를 확보해야 하기 때문이다. CronJob은
 * 완료 순서를 보장하지 않으므로 매니페스트가 시각으로 벌려 둔다.
 *
 * **갈래마다 Step 하나**다. 한 갈래가 일일 한도에 걸려도 나머지 Step은 계속 돈다(AC-018).
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotDetailPrefetchJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val useCase: SpotDetailPrefetchUseCase,
    private val properties: SpotDetailPrefetchProperties,
) {

    @Bean
    fun spotDetailPrefetchJob(): Job {
        val steps = SpotDetailPart.entries.map(::prefetchStep)
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
            .build()
    }

    /**
     * 한 갈래의 수집 Step. `allowStartIfComplete(false)`(기본값)라, 재실행 시 이미 완료된 갈래는
     * 건너뛰고 중단된 갈래부터 재개한다.
     */
    private fun prefetchStep(part: SpotDetailPart): Step =
        StepBuilder("${JOB_NAME}Step-$part", jobRepository)
            .tasklet(
                SpotDetailPrefetchTasklet(useCase, part, properties.batchSize, properties.dailyCallBudget),
                transactionManager,
            )
            .build()

    companion object {
        const val JOB_NAME = "spotDetailPrefetchJob"
    }
}
