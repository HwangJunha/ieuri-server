package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * tourism 도메인 인프라 빈 구성. 설정값(TourApiProperties)에서 application 정책(SpotQueryPolicy)을 뽑아
 * 서비스에 주입한다. @ConfigurationPropertiesScan이 없으므로 여기서 명시적으로 활성화한다(프로젝트 관례).
 */
@Configuration
@EnableConfigurationProperties(TourApiProperties::class)
class TourismConfig {

    @Bean
    fun spotQueryPolicy(properties: TourApiProperties): SpotQueryPolicy =
        SpotQueryPolicy(
            maxRadiusMeters = properties.maxRadiusMeters,
            maxPageSize = properties.maxPageSize,
            upstreamPageSize = properties.upstreamPageSize,
            maxUpstreamPagesPerCursor = properties.maxUpstreamPagesPerCursor,
        )
}
