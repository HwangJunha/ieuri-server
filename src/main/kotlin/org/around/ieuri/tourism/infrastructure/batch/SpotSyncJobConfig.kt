package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 관광지 목록 동기화 잡 구성(SPEC-0004 §4.1).
 *
 * ```
 * spotSyncJob
 * └── syncLanguageStep ×4 (KO → EN → JA → ZH, 순차)
 * ```
 *
 * **언어별 Step 분리**는 AC-7(부분 커밋)·AC-8(재개)이 요구하는 실패 지점 재개를 Spring Batch가 기본
 * 제공하는 재시작 지점과 맞물리게 하기 위함이다. 4개 언어를 한 Step에 넣으면 재개 지점 표현이 복잡해진다.
 *
 * **순차 실행**은 serviceKey가 4개 언어 공용 단일 키이기 때문이다(SPEC-0002 §0 B-4 실측) — 병렬화하면
 * 호출 한도를 4배 속도로 소모한다.
 *
 * 정리 단계(cleanupStep)는 아직 없다. 사라진 항목 처리 방식이 `[TBD-1]`, 허용 창이 `[TBD-1a]`로 미결이며,
 * 둘은 AC-13과 직접 충돌하는 관계라 임의로 정할 수 없다(스펙 런타임 계약 위험 10번). 결정 전에 정리를
 * 넣으면 멀쩡한 관광지를 지우는 쪽이든 AC-13을 어기는 쪽이든 한쪽을 몰래 택하는 셈이 된다.
 * `last_synced_at`은 이미 적재되고 있으므로 결정 후 Step 추가만으로 붙는다.
 */
@Configuration
class SpotSyncJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val properties: SpotSyncProperties,
) {

    @Bean
    fun spotSyncJob(): Job {
        // KO 먼저 — 데이터가 가장 많고 실측 대상이라 문제가 있으면 여기서 먼저 드러난다.
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        val builder = JobBuilder(JOB_NAME, jobRepository).start(syncStep(order.first()))
        return order.drop(1)
            .fold(builder) { acc, language -> acc.next(syncStep(language)) }
            .build()
    }

    /**
     * 언어 1개를 전량 수집하는 Step. `allowStartIfComplete(false)`(기본값)라, 재실행 시 이미 완료된 언어
     * Step은 건너뛰고 실패한 언어부터 재개한다(AC-8).
     */
    private fun syncStep(language: DomainLocale): Step =
        StepBuilder("syncLanguageStep-${language.name}", jobRepository)
            .tasklet(
                SpotSyncTasklet(
                    language = language,
                    syncPort = syncPort,
                    storePort = storePort,
                    pageSize = properties.pageSize,
                ),
                transactionManager,
            )
            .build()

    companion object {
        const val JOB_NAME = "spotSyncJob"
    }
}
