package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncJobConfig
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncProperties
import org.springframework.batch.core.job.SimpleJob
import org.springframework.batch.core.repository.JobRepository
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 잡의 Step 구성과 순서(SPEC-0007 §4.2).
 *
 * **축제 수집이 관광지 수집 뒤에 와야 한다**(R-2·AC-5). `spot_festival`이 `spot`에 FK를 걸므로 부모
 * 행이 먼저 있어야 하며, 반대 순서면 첫 실행에서 축제가 전부 건너뛰어진다. 순서는 잡 조립 시점에
 * 결정되므로 Spring 컨텍스트 없이 [SpotSyncJobConfig]를 직접 만들어 확인한다 — 배선이 아니라
 * **어떤 Step이 어떤 순서로 들어가는가**가 검증 대상이다.
 */
class FestivalSyncOrderingTest {

    private val config = SpotSyncJobConfig(
        jobRepository = mockk<JobRepository>(relaxed = true),
        transactionManager = mockk<PlatformTransactionManager>(relaxed = true),
        syncPort = mockk<SpotSyncPort>(),
        storePort = mockk<SpotStorePort>(),
        properties = SpotSyncProperties(),
        festivalSyncPort = mockk<FestivalSyncPort>(),
        festivalStorePort = mockk<FestivalStorePort>(),
        festivalProperties = FestivalSyncProperties(),
    )

    private val stepNames: List<String>
        get() = (config.spotSyncJob() as SimpleJob).stepNames.toList()

    private val festivalJobStepNames: List<String>
        get() = (config.festivalSyncJob() as SimpleJob).stepNames.toList()

    private fun isFestivalStep(name: String) = name.contains("-festivalStep-")

    @Test
    fun `모든 관광지 Step이 끝난 뒤에 축제 Step이 온다`() {
        // AC-5. 부모 spot 행이 먼저 적재되어야 축제 자식 행이 붙을 수 있다.
        val lastSpot = stepNames.indexOfLast { it.startsWith("syncLanguageStep-") }
        val firstFestival = stepNames.indexOfFirst(::isFestivalStep)

        assertTrue(firstFestival > lastSpot, "축제 Step이 관광지 Step보다 먼저 온다: $stepNames")
    }

    @Test
    fun `4개 언어 각각에 대해 축제 Step이 하나씩 있다`() {
        // AC-20·R-6. 언어별로 독립 Step이라 한 언어의 실패가 다른 언어를 되돌리지 않는다.
        val festivalSteps = stepNames.filter(::isFestivalStep)

        assertEquals(
            DomainLocale.entries.map { "${SpotSyncJobConfig.JOB_NAME}-festivalStep-${it.name}" }.toSet(),
            festivalSteps.toSet(),
        )
        assertEquals(4, festivalSteps.size)
    }

    // ---------- 축제 선별 실행 잡 ----------

    @Test
    fun `축제 전용 잡은 축제 Step만 갖는다`() {
        // 관광지 수집은 4개 언어 전량(약 12만 건)을 훑어 TourAPI 호출을 크게 소모한다.
        // 축제만 갱신하면 되는 상황에서 그것까지 다시 돌 이유가 없다.
        assertEquals(SpotSyncJobConfig.FESTIVAL_JOB_NAME, config.festivalSyncJob().name)
        assertEquals(4, festivalJobStepNames.size)
        assertTrue(
            festivalJobStepNames.all(::isFestivalStep),
            "축제 전용 잡에 관광지 Step이 섞였다: $festivalJobStepNames",
        )
    }

    @Test
    fun `두 잡의 Step 이름이 겹치지 않는다`() {
        // 같은 이름을 쓰면 두 잡이 같은 BATCH_STEP_EXECUTION 이력을 공유해 재개 판정이 서로
        // 간섭한다 — 한 잡이 완료 표시한 Step을 다른 잡이 건너뛴다.
        assertTrue(
            stepNames.intersect(festivalJobStepNames.toSet()).isEmpty(),
            "Step 이름 충돌: ${stepNames.intersect(festivalJobStepNames.toSet())}",
        )
    }

    @Test
    fun `정기 실행 잡은 관광지와 축제를 한 잡에서 순서대로 돈다`() {
        // 스펙 §선행 조사·§비범위. 정기 실행을 두 잡으로 나누면 각자의 정리가 서로의 행을 지우고,
        // 관광지 수집이 종료 축제를 되살린다 — R-9가 성립하려면 한 잡 안에서 순서가 보장되어야 한다.
        //
        // festivalSyncJob은 이 결정을 뒤집지 않는다. **정기 실행이 아니라 수동 보정용**이며,
        // 그 잡만 돌면 R-9가 성립하지 않는다는 점을 festivalSyncJob의 KDoc이 명시한다.
        assertEquals(SpotSyncJobConfig.JOB_NAME, config.spotSyncJob().name)
        assertEquals(8, stepNames.size)
    }
}
