package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.spot.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.spot.SpotDetailPrefetchService
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotDetailReadUseCase
import org.around.ieuri.tourism.application.spot.required.TourInfoPort
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
        detailRead: SpotDetailReadUseCase,
        tourInfoPort: TourInfoPort,
        spotCommandUseCase: SpotCommandUseCase,
        policy: SpotDetailPrefetchPolicy,
    ): SpotDetailPrefetchUseCase = SpotDetailPrefetchService(detailRead, tourInfoPort, spotCommandUseCase, policy)
}
