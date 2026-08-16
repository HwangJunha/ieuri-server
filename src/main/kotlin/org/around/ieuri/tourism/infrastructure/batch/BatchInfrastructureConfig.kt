package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 배치 메타데이터 저장소를 DB(JDBC JobRepository)로 고정한다. batch 프로파일에서만 로드된다.
 *
 * Boot 4 / Batch 6의 기본값은 인메모리 `ResourcelessJobRepository`이며, 그대로 두면 중복 실행 방지와
 * 실패 지점 재개가 **오류 없이 조용히** 무력화된다(메타데이터가 프로세스와 함께 버려진다).
 *
 * `dataSourceRef`·`transactionManagerRef`는 생략한다 — 기본값이 Boot가 만드는 빈 이름과 일치한다.
 * 메타 테이블 스키마는 Flyway가 소유하므로 Batch 자체 초기화를 켜지 않는다.
 *
 * [tablePrefix]가 메타 테이블의 `batch` 스키마를 가리킨다(SPEC-0023 R2). **`spring.batch.jdbc.
 * table-prefix`로는 안 된다** — Boot 4에 없는 키라 조용히 무시되고, 배치는 여전히 search_path로
 * 테이블을 찾는다. 접두사이지 스키마 설정이 아니므로 마지막 `.`이 필요하다.
 */
@Configuration
@Profile(BATCH_PROFILE)
@EnableBatchProcessing
@EnableJdbcJobRepository(tablePrefix = "batch.BATCH_")
class BatchInfrastructureConfig
