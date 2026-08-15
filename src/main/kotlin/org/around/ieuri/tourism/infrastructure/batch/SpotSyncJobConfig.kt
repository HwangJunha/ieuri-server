package org.around.ieuri.tourism.infrastructure.batch

import tools.jackson.databind.ObjectMapper
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

/**
 * 관광지·축제 동기화 잡 구성.
 *
 * ```
 * spotSyncJob
 * ├── syncLanguageStep       ×4 (KO → EN → JA → ZH, 순차)
 * ├── festivalSyncStep       ×4 (KO → EN → JA → ZH, 순차)
 * ├── catalogAssignmentStep  카탈로그 배정 (SPEC-0019)
 * └── catalogTranslationStep 신규분 번역
 * ```
 *
 * **축제 Step이 관광지 Step 뒤에 와야 한다** — `spot_festival`이 `spot`에 FK를 걸므로 부모 행이
 * 먼저 있어야 하고, 순서가 뒤집히면 첫 실행에서 축제가 전부 건너뛰어진다.
 *
 * **카탈로그 Step은 적재 뒤에 온다**(SPEC-0019 R-7) — 적재되지 않은 행은 배정할 것이 없다.
 * 비관광 spot은 적재 시점에 이미 빠져 있다(SPEC-0018).
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
    private val catalogUseCase: SpotCatalogUseCase,
    private val catalogProperties: SpotCatalogProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun spotSyncJob(): Job {
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        // 관광지 4개 언어를 모두 마친 뒤 축제 4개 언어가 온다 — 부모 행이 먼저 있어야 한다.
        // 카탈로그 배정·번역·보고는 그 뒤다(SPEC-0019 R-7).
        val steps = order.map(::syncStep) +
            order.map(::festivalStep) +
            listOf(catalogAssignmentStep(), catalogTranslationStep())
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
            .build()
    }

    /** 미배정 spot을 카탈로그에 배정한다. 대상이 소진될 때까지 회차를 반복한다. */
    private fun catalogAssignmentStep(): Step =
        StepBuilder("catalogAssignmentStep", jobRepository)
            .tasklet(SpotCatalogAssignmentTasklet(catalogUseCase), transactionManager)
            .build()


    /** 번역이 비어 있는 (카탈로그, 언어) 쌍을 채운다. 재번역 경로는 없다(R-7). */
    private fun catalogTranslationStep(): Step =
        StepBuilder("catalogTranslationStep", jobRepository)
            .tasklet(
                SpotCatalogTranslationTasklet(catalogUseCase, catalogProperties.translationBatchSize),
                transactionManager,
            )
            .build()

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
