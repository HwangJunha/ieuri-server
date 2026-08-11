package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.DistrictRepresentativeUpdatePort
import org.around.ieuri.tourism.infrastructure.config.DistrictRepresentativeProperties
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
 * 시군구 대표 위치 선정 · 카테고리 집계 잡 구성(SPEC-0015 · V46 · V50). Step 하나짜리 잡이다 —
 * 전부 DB 안의 UPDATE 문장이라 페이지네이션도 언어별 분기도 없다. 둘이 한 Step인 이유는
 * [DistrictRepresentativeLocationTasklet] 주석 참조.
 *
 * **반드시 spot 동기화(`spotSyncJob`) 이후에 실행해야 한다.** `spot` 테이블을 읽어 산출하므로
 * 순서가 뒤집히면 전날 데이터로 계산한다. 그럼에도 `spotSyncJob`에 Step으로 붙이지 않는다 —
 * 런처가 이름으로 잡 하나를 골라 실행하는 구조이고 둘은 별도 CronJob으로 트리거된다.
 * **순서 보장은 CronJob 스케줄의 책임이다**(예: spot 04:00 → 이 잡 06:00).
 *
 * 실행: `--ieuri.tourapi.sync.job=districtRepresentativeLocationJob`
 */
@Configuration
@Profile(BATCH_PROFILE)
class DistrictRepresentativeLocationJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
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
