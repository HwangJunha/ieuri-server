package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 카탈로그 배정·번역·보고 배치 설정(SPEC-0019).
 *
 * @param translationBatchSize 한 회차에 번역할 카탈로그 수. 외부 호출 비용이 들어 회차를 나눈다.
 */
@ConfigurationProperties(prefix = "ieuri.catalog")
data class SpotCatalogProperties(
    val translationBatchSize: Int = 200,
)
