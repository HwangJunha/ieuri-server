package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogSplitUseCase
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalUpsertPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotUpsertPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncJobConfig
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncProperties
import org.springframework.batch.core.job.SimpleJob
import org.springframework.batch.core.repository.JobRepository
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 잡의 Step 구성과 순서(SPEC-0007 §4.2). [SpotSyncJobConfig]를 직접 만들어 확인한다.
 * - 축제 수집은 관광지 수집 뒤다(R-2·AC-5) — `spot_festival`이 `spot`에 FK를 건다.
 */
class FestivalSyncOrderingTest {

    private val config = SpotSyncJobConfig(
        jobRepository = mockk<JobRepository>(relaxed = true),
        transactionManager = mockk<PlatformTransactionManager>(relaxed = true),
        syncPort = mockk<SpotSyncPort>(),
        storePort = mockk<SpotStorePort>(),
        upsertPort = mockk<SpotUpsertPort>(),
        properties = SpotSyncProperties(),
        festivalSyncPort = mockk<FestivalSyncPort>(),
        festivalStorePort = mockk<FestivalStorePort>(),
        festivalUpsertPort = mockk<FestivalUpsertPort>(),
        festivalProperties = FestivalSyncProperties(),
        catalogUseCase = mockk<SpotCatalogUseCase>(),
        catalogSplitUseCase = mockk<SpotCatalogSplitUseCase>(),
        objectMapper = mockk<ObjectMapper>(),
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
    fun `4개 언어 각각에 대해 축제 Step이 하나씩 있다`() {
        // AC-20·R-6. 언어별로 독립 Step이라 한 언어의 실패가 다른 언어를 되돌리지 않는다.
        val festivalSteps = stepNames.filter { it.startsWith("festivalSyncStep-") }

        assertEquals(DomainLocale.entries.map { "festivalSyncStep-${it.name}" }.toSet(), festivalSteps.toSet())
        assertEquals(4, festivalSteps.size)
    }

    @Test
    fun `축제 전용 잡을 따로 만들지 않는다`() {
        // 관광지·축제 정리가 서로의 행을 지우지 않도록 한 잡 안에서 순서를 보장한다(R-9).
        assertEquals(SpotSyncJobConfig.JOB_NAME, config.spotSyncJob().name)
        // 관광지 4 + 축제 4 + 카탈로그 배정 1 + 분할 1.
        assertEquals(10, stepNames.size)
    }

    @Test
    fun `카탈로그 분할 Step이 배정 Step 뒤에 온다`() {
        // SPEC-0029. 분할은 배정이 전량 끝난 뒤여야 한다.
        assertTrue(
            stepNames.indexOf("catalogSplitStep") > stepNames.indexOf("catalogAssignmentStep"),
            "분할이 배정보다 먼저 온다: $stepNames",
        )
    }
}
