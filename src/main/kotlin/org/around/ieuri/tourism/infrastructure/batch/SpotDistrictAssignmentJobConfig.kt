package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.SpotDistrictAssignmentPort
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
 * 카탈로그 시군구 귀속 잡 구성(SPEC-0030 R11 · V75). Step 하나짜리 잡이다 — 귀속 전체가 DB
 * 안의 UPDATE 한 문장이라 페이지네이션도 언어별 분기도 없다.
 *
 * **반드시 spot 동기화(`spotSyncJob`) 이후에 실행해야 한다.** 새로 적재된 카탈로그를 귀속시키는
 * 것이 목적이므로 순서가 뒤집히면 그 장소들이 하루 늦게 지역 상세에 나타난다. 그럼에도
 * `spotSyncJob`에 Step으로 붙이지 않는다 — 런처가 이름으로 잡 하나를 골라 실행하는 구조이고
 * 둘은 별도 CronJob으로 트리거된다. **순서 보장은 CronJob 스케줄의 책임이다**
 * ([DistrictRepresentativeLocationJobConfig]와 같은 규약: spot 04:00 → 이 잡 06:00).
 *
 * **적재 경로가 아니라 별도 잡인 이유**는 귀속이 도메인 규칙이면서 집합 연산이기 때문이다.
 * 카탈로그 적재 서비스에 얹으면 신규 spot마다 공간 조회가 붙고 `CatalogStandardValues`(도메인
 * 값 객체)가 시군구를 알게 된다. DB 트리거로 두면 그 규칙이 DB에 갇혀 교체가 어려워진다.
 *
 * 실행: `--ieuri.tourapi.sync.job=spotDistrictAssignmentJob`
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotDistrictAssignmentJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val assignmentPort: SpotDistrictAssignmentPort,
) {

    @Bean
    fun spotDistrictAssignmentJob(): Job {
        val step: Step = StepBuilder("${JOB_NAME}Step", jobRepository)
            .tasklet(SpotDistrictAssignmentTasklet(assignmentPort), transactionManager)
            .build()
        return JobBuilder(JOB_NAME, jobRepository).start(step).build()
    }

    companion object {
        const val JOB_NAME = "spotDistrictAssignmentJob"
    }
}
