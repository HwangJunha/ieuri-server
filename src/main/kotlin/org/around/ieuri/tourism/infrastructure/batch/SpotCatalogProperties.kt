package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 카탈로그 배정·번역·보고 배치 설정(SPEC-0019).
 *
 * @param translationBatchSize 한 회차에 번역할 카탈로그 수. 외부 호출 비용이 들어 회차를 나눈다.
 * @param unresolvedReportPath 미해결 CSV 출력 경로. **발송 경로는 규정하지 않는다** — 파일을
 *   어떻게 가져갈지는 운영자가 정한다(R-8).
 * @param translationSeedPath 초기 번역 시드(JSON) 경로. planner 하네스가 만든 34,622건이며,
 *   **일회성이다** — 적재가 끝나면 이 Step은 매번 0건으로 끝난다. 값이 비면 시드 Step을 건너뛴다.
 */
@ConfigurationProperties(prefix = "ieuri.tourism.catalog")
data class SpotCatalogProperties(
    val translationBatchSize: Int = 200,
    val unresolvedReportPath: String = "build/reports/spot-catalog-unresolved.csv",
    val translationSeedPath: String = "",
)
