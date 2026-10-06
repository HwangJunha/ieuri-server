package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.detail.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.detail.SpotDetailPrefetchService
import org.around.ieuri.tourism.application.detail.provided.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.detail.required.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.application.detail.required.SpotDetailRepository
import org.around.ieuri.tourism.application.shared.required.TourInfoPort
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
        detailRepository: SpotDetailRepository,
        policy: SpotDetailPrefetchPolicy,
    ): SpotDetailPrefetchUseCase = SpotDetailPrefetchService(targetQueryPort, tourInfoPort, detailRepository, policy)
}
