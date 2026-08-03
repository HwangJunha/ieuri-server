package org.around.ieuri.tourism.infrastructure.batch

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
 * 관광지 목록 동기화 잡 구성(SPEC-0004 §4.1, SPEC-0007 §4).
 *
 * ```
 * spotSyncJob
 * ├── syncLanguageStep     ×4 (KO → EN → JA → ZH, 순차)
 * └── festivalSyncStep     ×4 (KO → EN → JA → ZH, 순차)
 * ```
 *
 * **축제를 별도 잡으로 만들지 않는다**(SPEC-0007 §선행 조사). `areaBasedSyncList2`에는
 * `contentTypeId` 요청 파라미터가 없어 축제가 이미 관광지 경로로 `spot`에 적재되므로, 별도 잡이
 * 종료 축제를 지우면 다음 관광지 수집이 같은 행사를 되살린다. 또 두 잡이 각각 `spot`을 정리하면
 * `deleteMissing`이 서로의 행을 지운다 — 한 잡 안에서 순서가 보장되어야 R-9가 성립한다.
 *
 * **축제 수집이 관광지 수집 뒤에 온다.** `spot_festival`이 `spot`에 FK를 걸므로 부모 행이 먼저 있어야
 * 한다(R-2·AC-5). 반대 순서면 첫 실행에서 축제가 전부 건너뛰어진다.
 *
 * 종료 축제 제외 집합(R-9)은 Step 생성 시점이 아니라 **Step 실행 시점에** 조회된다 — 빈은 부팅 때
 * 만들어지므로 그때 잡은 집합은 실행 시점에 낡는다. Tasklet에 조회 함수를 넘겨 시점을 늦춘다.
 *
 * **언어별 Step 분리**는 AC-7(부분 커밋)·AC-8(재개)이 요구하는 실패 지점 재개를 Spring Batch가 기본
 * 제공하는 재시작 지점과 맞물리게 하기 위함이다. 4개 언어를 한 Step에 넣으면 재개 지점 표현이 복잡해진다.
 *
 * **순차 실행**은 serviceKey가 4개 언어 공용 단일 키이기 때문이다(SPEC-0002 §0 B-4 실측) — 병렬화하면
 * 호출 한도를 4배 속도로 소모한다.
 *
 * **사라진 항목 정리는 별도 Step이 아니라 각 언어 Step의 마지막에 수행한다**(R-5). 언어별로 수집한
 * contentId 집합이 그 시점에 메모리에 있으므로, 그것을 그대로 쓰는 편이 자연스럽다. 별도 Step으로
 * 빼면 집합을 어딘가에 넘겨야 하는데 6만 건을 ExecutionContext에 직렬화하는 것은 비싸다.
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
) {

    @Bean
    fun spotSyncJob(): Job {
        // KO 먼저 — 데이터가 가장 많고 실측 대상이라 문제가 있으면 여기서 먼저 드러난다.
        val order = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
        // 관광지 4개 언어를 모두 마친 뒤 축제 4개 언어가 온다 — 부모 행이 먼저 있어야 한다(R-2).
        val steps = order.map(::syncStep) + order.map(::festivalStep)
        return steps.drop(1)
            .fold(JobBuilder(JOB_NAME, jobRepository).start(steps.first())) { acc, step -> acc.next(step) }
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
                    cleanupMinRatio = properties.cleanupMinRatio,
                    // 조회를 Step 실행 시점으로 미룬다(R-9). 이 람다가 없으면 부팅 때 잡은 낡은
                    // 집합으로 걸러 종료 축제가 되살아난다.
                    endedFestivalIds = { runDate -> festivalStorePort.findEndedContentIds(language, runDate) },
                ),
                transactionManager,
            )
            .build()

    /**
     * 언어 1개의 축제 정보를 수집·적재하고 정리하는 Step(SPEC-0007). 언어별로 나누는 이유와 순차
     * 실행의 근거는 관광지 Step과 같다 — 실패 지점 재개(AC-37)와 공용 serviceKey다.
     *
     * **한 언어의 실패가 다른 언어를 되돌리지 않는다**(R-6·AC-21). Step이 분리되어 있어 앞 언어의
     * 커밋은 그대로 남는다.
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
