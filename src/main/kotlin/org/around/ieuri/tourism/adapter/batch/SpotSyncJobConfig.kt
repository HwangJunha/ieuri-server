package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCurationUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotSyncUseCase
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

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
    private val syncUseCase: SpotSyncUseCase,
    private val curationUseCase: SpotCatalogCurationUseCase,
    private val properties: SpotSyncProperties,
    private val festivalProperties: FestivalSyncProperties,
) {

    @Bean
    fun spotSyncJob(): Job {
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        val steps = order.map { syncStep("syncLanguageStep-${it.name}", it, SpotSyncTasklet.Target.SPOT, properties.pageSize) } +
            order.map { syncStep("festivalSyncStep-${it.name}", it, SpotSyncTasklet.Target.FESTIVAL, festivalProperties.pageSize) } +
            listOf(
                step("catalogAssignmentStep", SpotCatalogAssignmentTasklet(curationUseCase)),
                step("catalogSplitStep", SpotCatalogSplitTasklet(curationUseCase)),
            )
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
            .build()
    }

    /** 언어 1개를 전량 수집하는 Step. 재실행 시 완료된 언어는 건너뛰고 실패한 언어부터 재개한다. */
    private fun syncStep(name: String, language: DomainLocale, target: SpotSyncTasklet.Target, pageSize: Int): Step = step(name, SpotSyncTasklet(language, target, syncUseCase, pageSize))

    private fun step(name: String, tasklet: Tasklet): Step = StepBuilder(name, jobRepository)
        .tasklet(tasklet)
        .build()

    companion object {
        const val JOB_NAME = "spotSyncJob"
    }
}
