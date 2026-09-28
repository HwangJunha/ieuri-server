package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.transaction.PlatformTransactionManager

/**
 * 관광지 상세 사전 수집 잡(SPEC-0037). `spotSyncJob`과 별개 잡이며, 목록 수집 뒤로 CronJob 시각을 벌려 둔다.
 * - 갈래마다 Step 하나이고 넷을 동시에 돌린다. 한 갈래가 일일 한도에 걸려도 나머지는 계속 돈다(AC-018).
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
        val flows = SpotDetailPart.entries.map { part ->
            FlowBuilder<Flow>("${JOB_NAME}Flow-$part").start(prefetchStep(part)).build()
        }
        val parallel = FlowBuilder<Flow>("${JOB_NAME}Flow")
            .start(flows.first())
            .split(SimpleAsyncTaskExecutor("$JOB_NAME-"))
            .add(*flows.drop(1).toTypedArray())
            .build()
        return JobBuilder(JOB_NAME, jobRepository)
            .start(parallel)
            .build()
            .build()
    }

    /** 한 갈래의 수집 Step. 재실행 시 완료된 갈래는 건너뛰고 중단된 갈래부터 재개한다. */
    private fun prefetchStep(part: SpotDetailPart): Step = StepBuilder("${JOB_NAME}Step-$part", jobRepository)
        .tasklet(
            SpotDetailPrefetchTasklet(useCase, part, properties.batchSize, properties.dailyLimit),
            transactionManager,
        )
        .build()

    companion object {
        const val JOB_NAME = "spotDetailPrefetchJob"
    }
}
