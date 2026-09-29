package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.policy.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.application.service.SpotDetailPrefetchService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** 상세 사전 수집의 빈 조립(SPEC-0037). batch 프로파일에서만 로드된다. */
@Configuration
@Profile(BATCH_PROFILE)
@EnableConfigurationProperties(SpotDetailPrefetchProperties::class)
class SpotDetailPrefetchConfig {

    @Bean
    fun spotDetailPrefetchPolicy(properties: SpotDetailPrefetchProperties): SpotDetailPrefetchPolicy = SpotDetailPrefetchPolicy(
        refreshAfter = properties.refreshAfter,
        callsPerSecond = properties.callsPerSecond,
    )

    @Bean
    fun spotDetailPrefetchService(
        targetQueryPort: SpotDetailPrefetchTargetQueryPort,
        tourInfoPort: TourInfoPort,
        upsertPort: SpotDetailUpsertPort,
        policy: SpotDetailPrefetchPolicy,
    ): SpotDetailPrefetchUseCase = SpotDetailPrefetchService(targetQueryPort, tourInfoPort, upsertPort, policy)
}
