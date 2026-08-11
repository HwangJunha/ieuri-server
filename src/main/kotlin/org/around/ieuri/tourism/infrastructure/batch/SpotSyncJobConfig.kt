package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.PetFriendlyStorePort
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
 * 관광지·축제·반려동물 동기화 잡 구성.
 *
 * ```
 * spotSyncJob
 * ├── syncLanguageStep     ×4 (KO → EN → JA → ZH, 순차)
 * ├── festivalSyncStep     ×4 (KO → EN → JA → ZH, 순차)
 * └── petFriendlySyncStep  ×1 (국문 전용)
 * ```
 *
 * **축제 Step이 관광지 Step 뒤에 와야 한다** — `spot_festival`이 `spot`에 FK를 걸므로 부모 행이
 * 먼저 있어야 하고, 순서가 뒤집히면 첫 실행에서 축제가 전부 건너뛰어진다.
 *
 * **반려동물 Step은 마지막이다.** `content_id`로 국문 spot을 참조하므로 spot이 먼저 적재되어야
 * 집계가 맞는다. FK가 없어 순서가 뒤집혀도 터지지는 않지만, 그날의 신규 spot이 반려동물 집계에서
 * 빠진다.
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
    private val petFriendlySyncPort: PetFriendlySyncPort,
    private val petFriendlyStorePort: PetFriendlyStorePort,
    private val petFriendlyProperties: PetFriendlySyncProperties,
) {

    @Bean
    fun spotSyncJob(): Job {
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        // 관광지 4개 언어 → 축제 4개 언어 → 반려동물. 앞의 것이 뒤의 것의 참조 대상이다.
        val steps = order.map(::syncStep) + order.map(::festivalStep) + petFriendlyStep()
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
                    cleanupMinRatio = properties.cleanupMinRatio,
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

    /**
     * 반려동물 동반 가능 관광지를 전량 수집·적재하는 Step. **언어 분기가 없다** —
     * `detailPetTour2`는 KorService2에만 있다(SPEC-0015 · V48).
     */
    private fun petFriendlyStep(): Step =
        StepBuilder("petFriendlySyncStep", jobRepository)
            .tasklet(
                PetFriendlySyncTasklet(
                    syncPort = petFriendlySyncPort,
                    storePort = petFriendlyStorePort,
                    pageSize = petFriendlyProperties.pageSize,
                ),
                transactionManager,
            )
            .build()

    companion object {
        const val JOB_NAME = "spotSyncJob"
    }
}
