package org.around.ieuri.tourism.adapter.batch

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 배치 메타데이터 저장소를 DB(JDBC JobRepository)로 고정한다. batch 프로파일에서만 로드된다.
 * - 빠지면 Boot 4 기본값(인메모리)이 되어 중복 실행 방지와 재개가 조용히 무력화된다.
 * - 메타 테이블 스키마는 Flyway가 소유한다.
 * - [tablePrefix]가 `batch` 스키마를 가리킨다(SPEC-0023 R2). `spring.batch.jdbc.table-prefix`는 Boot 4에서 무시된다. 마지막 `.`이 필요하다.
 */
@Configuration
@Profile(BATCH_PROFILE)
@EnableBatchProcessing
@EnableJdbcJobRepository(tablePrefix = "batch.BATCH_")
class BatchInfrastructureConfig
