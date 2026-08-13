package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.application.port.`in`.command.SpotActivationUseCase
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.SpotExclusionTasklet
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
        activationUseCase = mockk<SpotActivationUseCase>(),
    )

    private val stepNames: List<String>
        get() = (config.spotSyncJob() as SimpleJob).stepNames.toList()

    @Test
    fun `모든 관광지 Step이 끝난 뒤에 축제 Step이 온다`() {
        // AC-5. 부모 spot 행이 먼저 적재되어야 축제 자식 행이 붙을 수 있다.
        val lastSpot = stepNames.indexOfLast { it.startsWith("syncLanguageStep-") }
        val firstFestival = stepNames.indexOfFirst { it.startsWith("festivalSyncStep-") }

        assertTrue(firstFestival > lastSpot, "축제 Step이 관광지 Step보다 먼저 온다: $stepNames")
    }

    @Test
    fun `비관광 판정 Step이 적재·정리 Step 뒤에 온다`() {
        // SPEC-0018. 삭제될 행에 판정을 적용하는 것은 낭비이고, 순서가 반대면 판정 건수와
        // 실제 비활성 행 수가 어긋난다.
        val judgement = stepNames.indexOf(SpotExclusionTasklet.STEP_NAME)
        val lastSpot = stepNames.indexOfLast { it.startsWith("syncLanguageStep-") }

        assertTrue(judgement > lastSpot, "판정 Step이 관광지 Step보다 먼저 온다: $stepNames")
    }

    @Test
    fun `비관광 판정 Step은 언어마다 생기지 않는다`() {
        // 판정 대상이 언어를 가르지 않는 전량이라 하나뿐이어야 한다 — 언어마다 돌면 같은 일을
        // 네 번 하고, 언어 간 전파(R8)가 전 언어 적재 이전에 돌아 무너진다.
        assertEquals(1, stepNames.count { it == SpotExclusionTasklet.STEP_NAME })
    }

    @Test
    fun `4개 언어 각각에 대해 축제 Step이 하나씩 있다`() {
        // AC-20·R-6. 언어별로 독립 Step이라 한 언어의 실패가 다른 언어를 되돌리지 않는다.
        val festivalSteps = stepNames.filter { it.startsWith("festivalSyncStep-") }

        assertEquals(DomainLocale.entries.map { "festivalSyncStep-${it.name}" }.toSet(), festivalSteps.toSet())
        assertEquals(4, festivalSteps.size)
    }

    @Test
    fun `축제 전용 잡을 따로 만들지 않는다`() {
        // 스펙 §선행 조사·§비범위. 별도 잡이면 두 잡의 정리가 서로의 행을 지우고, 관광지 수집이
        // 종료 축제를 되살린다 — R-9가 성립하려면 한 잡 안에서 순서가 보장되어야 한다.
        assertEquals(SpotSyncJobConfig.JOB_NAME, config.spotSyncJob().name)
        // 언어 4 + 축제 4 + 비관광 판정 1(SPEC-0018). 판정도 이 잡 안에 있어야 한다 —
        // 따로 떼면 적재·정리와의 순서 보장이 사라진다.
        assertEquals(9, stepNames.size)
    }
}
