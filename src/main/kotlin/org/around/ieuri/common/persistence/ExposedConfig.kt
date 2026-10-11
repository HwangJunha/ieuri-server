package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Exposed 트랜잭션 매니저. 이 앱의 유일한 매니저라 `@Transactional`과 배치 JobRepository가 이름 지정 없이 쓴다.
 * - 메인 [DataSourceConfig.dataSource]를 그대로 쓴다 — 읽기 전용 트랜잭션은 그 라우팅을 따라 replica로 간다.
 * - 커넥션을 DataSource에 묶어 두므로 같은 DataSource의 `JdbcTemplate`(배치 메타 테이블)도 같은 트랜잭션에 합류한다.
 */
@Configuration
class ExposedConfig {

    /** 마이그레이션이 끝난 뒤에 만들어진다 — 어떤 트랜잭션도 마이그레이션 전 스키마를 보지 않는다. */
    @Bean
    @DependsOn("flyway")
    fun transactionManager(
        dataSource: DataSource,
        customizers: ObjectProvider<TransactionManagerCustomizers>,
    ): PlatformTransactionManager = SpringTransactionManager(dataSource, DatabaseConfig {}, false)
        .also { manager -> customizers.ifAvailable { it.customize(manager) } }
}
