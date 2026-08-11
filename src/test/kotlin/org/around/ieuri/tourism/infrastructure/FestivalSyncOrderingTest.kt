package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPort
import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.PetFriendlyStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.PetFriendlySyncProperties
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
        petFriendlySyncPort = mockk<PetFriendlySyncPort>(),
        petFriendlyStorePort = mockk<PetFriendlyStorePort>(),
        petFriendlyProperties = PetFriendlySyncProperties(),
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
        // 스펙 §선행 조사·§비범위. 별도 잡이면 두 잡의 정리가 서로의 행을 지우고, 관광지 수집이
        // 종료 축제를 되살린다 — R-9가 성립하려면 한 잡 안에서 순서가 보장되어야 한다.
        assertEquals(SpotSyncJobConfig.JOB_NAME, config.spotSyncJob().name)
        // 관광지 4 + 축제 4 + 반려동물 1.
        assertEquals(9, stepNames.size)
    }

    @Test
    fun `반려동물 Step이 마지막에 하나만 있다 — 국문 전용이라 언어 분기가 없다`() {
        // SPEC-0015 · V48. detailPetTour2는 KorService2에만 있어 4개 언어로 늘지 않는다.
        val petSteps = stepNames.filter { it.startsWith("petFriendlySyncStep") }

        assertEquals(listOf("petFriendlySyncStep"), petSteps)
        assertEquals(stepNames.size - 1, stepNames.indexOf("petFriendlySyncStep"))
    }

    @Test
    fun `반려동물 Step은 관광지 Step 뒤에 온다 — content_id로 spot을 참조한다`() {
        // FK가 없어 터지지는 않지만, 앞서면 그날 신규 spot이 집계에서 빠진다.
        val lastSpot = stepNames.indexOfLast { it.startsWith("syncLanguageStep-") }

        assertTrue(
            stepNames.indexOf("petFriendlySyncStep") > lastSpot,
            "반려동물 Step이 관광지 Step보다 먼저 온다: $stepNames",
        )
    }
}
