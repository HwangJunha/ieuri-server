package org.around.ieuri.architecture

import org.around.ieuri.tourism.adapter.batch.BatchInfrastructureConfig
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 배치 메타데이터가 `batch` 스키마를 가리키는지 검증한다(SPEC-0023 AC-7).
 * - 접두사는 `@EnableJdbcJobRepository(tablePrefix)`가 소유한다. `spring.batch.jdbc.table-prefix`는 Boot 4에서 무시된다.
 */
class BatchSchemaConfigTest {

    @Test
    fun `배치 메타 테이블 접두사가 batch 스키마를 가리킨다`() {
        val annotation = BatchInfrastructureConfig::class.java
            .getAnnotation(EnableJdbcJobRepository::class.java)

        // 접두사이지 스키마 설정이 아니라 마지막 `.`까지가 값이다.
        assertEquals("batch.BATCH_", annotation.tablePrefix)
    }
}
