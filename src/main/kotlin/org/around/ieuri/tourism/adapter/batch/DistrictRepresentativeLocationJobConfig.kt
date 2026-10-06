package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.adapter.config.DistrictRepresentativeProperties
import org.around.ieuri.tourism.application.shared.required.DistrictRepresentativeUpdatePort
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 시군구 대표 위치 선정 잡 구성(SPEC-0015 · V46). UPDATE 두 문장짜리 단일 Step이다.
 * - `spotSyncJob` 이후에 실행해야 한다 — 순서는 CronJob 스케줄이 보장한다(예: spot 04:00 → 이 잡 06:00).
 *
 * 실행: `--ieuri.tourapi.sync.job=districtRepresentativeLocationJob`
 */
@Configuration
@Profile(BATCH_PROFILE)
class DistrictRepresentativeLocationJobConfig(
    private val jobRepository: JobRepository,
    @Qualifier(ExposedConfig.EXPOSED_TX) private val transactionManager: PlatformTransactionManager,
    private val updatePort: DistrictRepresentativeUpdatePort,
    private val properties: DistrictRepresentativeProperties,
) {

    @Bean
    fun districtRepresentativeLocationJob(): Job {
        val step: Step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(
                DistrictRepresentativeLocationTasklet(
                    updatePort = updatePort,
                    cellSizeDegrees = properties.cellSizeDegrees,
                    minSpotCount = properties.minSpotCount,
                ),
                transactionManager,
            )
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_NAME = "districtRepresentativeLocationJob"
    }
}
