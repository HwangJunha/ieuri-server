package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.ClimateIndexSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.ClimateIndexStorePort
import org.around.ieuri.tourism.infrastructure.config.ClimateIndexProperties
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
 * 관광기후지수 동기화 잡 구성(SPEC-0015 · V49). 일 단위 1종이다.
 *
 * **`spotSyncJob`에 붙이지 않고 별도 잡으로 둔다** — 데이터 출처가 달라 실패 영향이 서로
 * 번질 이유가 없고, 런처가 잡 하나를 이름으로 골라 실행하는 구조다
 * (`--ieuri.tourapi.sync.job=districtClimateIndexSyncJob`).
 *
 * **호출은 1건이다** — 지역 파라미터를 생략하면 전국이 한 응답에 온다(실측 472행). 페이지가
 * 갈리면 그만큼만 늘어난다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class ClimateIndexSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: ClimateIndexSyncPort,
    private val storePort: ClimateIndexStorePort,
    private val properties: ClimateIndexProperties,
) {

    @Bean
    fun districtClimateIndexSyncJob(): Job {
        val tasklet = ClimateIndexSyncTasklet(
            syncPort = syncPort,
            storePort = storePort,
            days = properties.days,
            pageSize = properties.pageSize,
        )
        val step: Step = StepBuilder("${JOB_CLIMATE_INDEX}Step", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build()
        return JobBuilder(JOB_CLIMATE_INDEX, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_CLIMATE_INDEX = "districtClimateIndexSyncJob"
    }
}
