package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogSplitUseCase
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotUpsertPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.ObjectMapper

/**
 * 관광지·축제 동기화 잡 구성.
 *
 * ```
 * spotSyncJob
 * ├── syncLanguageStep       ×4 (KO → EN → JA → ZH, 순차)
 * ├── festivalSyncStep       ×4 (KO → EN → JA → ZH, 순차)
 * ├── catalogAssignmentStep  카탈로그 배정 (SPEC-0019)
 * └── catalogSplitStep       잘못 접힌 카탈로그 분할 (SPEC-0029)
 * ```
 *
 * - 축제 Step은 관광지 Step 뒤(`spot` FK), 카탈로그 Step은 적재 뒤다(SPEC-0019 R-7).
 * - 순차 실행이어야 한다 — serviceKey가 4개 언어 공용이라 병렬화하면 한도를 4배로 소모한다.
 */
@Configuration
@Profile(BATCH_PROFILE)
class SpotSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val upsertPort: SpotUpsertPort,
    private val properties: SpotSyncProperties,
    private val festivalSyncPort: FestivalSyncPort,
    private val festivalStorePort: FestivalStorePort,
    private val festivalUpsertPort: FestivalUpsertPort,
    private val festivalProperties: FestivalSyncProperties,
    private val catalogUseCase: SpotCatalogUseCase,
    private val catalogSplitUseCase: SpotCatalogSplitUseCase,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun spotSyncJob(): Job {
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        // 관광지 → 축제(부모 행이 먼저) → 카탈로그 배정(SPEC-0019 R-7).
        val steps = order.map(::syncStep) +
            order.map(::festivalStep) +
            listOf(catalogAssignmentStep(), catalogSplitStep())
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
            .build()
    }

    /** 미배정 spot을 카탈로그에 배정한다. 대상이 소진될 때까지 회차를 반복한다. */
    private fun catalogAssignmentStep(): Step = StepBuilder("catalogAssignmentStep", jobRepository)
        .tasklet(SpotCatalogAssignmentTasklet(catalogUseCase), transactionManager)
        .build()

    /** 서로 다른 장소가 접힌 카탈로그를 가른다(SPEC-0029). 배정 뒤여야 한다. */
    private fun catalogSplitStep(): Step = StepBuilder("catalogSplitStep", jobRepository)
        .tasklet(SpotCatalogSplitTasklet(catalogSplitUseCase, catalogUseCase), transactionManager)
        .build()

    /** 언어 1개를 전량 수집하는 Step. 재실행 시 완료된 언어는 건너뛰고 실패한 언어부터 재개한다. */
    private fun syncStep(language: DomainLocale): Step = StepBuilder("syncLanguageStep-${language.name}", jobRepository)
        .tasklet(
            SpotSyncTasklet(
                language = language,
                syncPort = syncPort,
                storePort = storePort,
                upsertPort = upsertPort,
                pageSize = properties.pageSize,
                // 조회를 Step 실행 시점으로 미룬다 — 부팅 때 잡은 집합이면 종료 축제가 되살아난다.
                endedFestivalIds = { runDate -> festivalStorePort.findEndedContentIds(language, runDate) },
            ),
            transactionManager,
        )
        .build()

    /** 언어 1개의 축제 정보를 수집·적재하고 정리하는 Step. */
    private fun festivalStep(language: DomainLocale): Step = StepBuilder("festivalSyncStep-${language.name}", jobRepository)
        .tasklet(
            FestivalSyncTasklet(
                language = language,
                syncPort = festivalSyncPort,
                storePort = festivalStorePort,
                upsertPort = festivalUpsertPort,
                pageSize = festivalProperties.pageSize,
            ),
            transactionManager,
        )
        .build()

    companion object {
        const val JOB_NAME = "spotSyncJob"
    }
}
