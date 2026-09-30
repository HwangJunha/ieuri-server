package org.around.ieuri.common.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Exposed 전용 DataSource·트랜잭션 매니저(SPEC-0039 D-33). 옮긴 context의 서비스가 [EXPOSED_TX]를 지정한다.
 * - 풀은 메인 풀의 hikari 설정(`search_path` 포함)을 복사하고 크기만 [ExposedPoolProperties]로 정한다.
 * - JPA 매니저를 여기서 직접 선언한다 — Boot의 JPA 매니저 자동구성은 매니저 빈이 하나라도 있으면 물러난다.
 */
@Configuration
@EnableConfigurationProperties(ExposedPoolProperties::class)
class ExposedConfig {

    @Bean(destroyMethod = "close")
    fun exposedPrimaryDataSource(
        @Qualifier("primaryDataSource") primary: DataSource,
        pool: ExposedPoolProperties,
    ): HikariDataSource = copyPool(primary, "exposed-primary", pool)

    /** 읽기 복제본이 없으면 null이다. */
    @Bean(destroyMethod = "close")
    fun exposedReplicaDataSource(
        @Qualifier("replicaDataSource") replica: DataSource?,
        pool: ExposedPoolProperties,
    ): HikariDataSource? = replica?.let { copyPool(it, "exposed-replica", pool) }

    /** 읽기 전용 트랜잭션은 replica로 간다 — 메인 [DataSourceConfig.dataSource]와 같은 라우팅이다. */
    @Bean
    fun exposedDataSource(
        @Qualifier("exposedPrimaryDataSource") primary: DataSource,
        @Qualifier("exposedReplicaDataSource") replica: DataSource?,
    ): DataSource {
        replica ?: return primary
        return LazyConnectionDataSourceProxy(ReadWriteRoutingDataSource(primary, replica))
    }

    @Bean(EXPOSED_TX)
    fun exposedTransactionManager(
        @Qualifier("exposedDataSource") dataSource: DataSource,
        customizers: ObjectProvider<TransactionManagerCustomizers>,
    ): PlatformTransactionManager = SpringTransactionManager(dataSource, DatabaseConfig {}, false)
        .also { manager -> customizers.ifAvailable { it.customize(manager) } }

    /** 이전하지 않은 context와 배치가 쓰는 기본 매니저. Boot 자동구성과 같은 모양이다. */
    @Bean
    @Primary
    fun transactionManager(customizers: ObjectProvider<TransactionManagerCustomizers>): PlatformTransactionManager = JpaTransactionManager().also { manager -> customizers.ifAvailable { it.customize(manager) } }

    private fun copyPool(source: DataSource, name: String, pool: ExposedPoolProperties): HikariDataSource {
        val config = HikariConfig()
        source.unwrap(HikariDataSource::class.java).copyStateTo(config)
        config.poolName = name
        config.maximumPoolSize = pool.maximumPoolSize
        config.minimumIdle = pool.minimumIdle
        return HikariDataSource(config)
    }

    companion object {
        /** `@Transactional(transactionManager = EXPOSED_TX)`로 쓴다. */
        const val EXPOSED_TX = "exposedTransactionManager"
    }
}

/**
 * Exposed 풀 크기. 쓰기·읽기 풀 각각에 적용된다.
 * - 메인 풀에 더해지는 커넥션이다. 올리기 전에 DB `max_connections` 여유를 확인한다.
 */
@ConfigurationProperties(prefix = "ieuri.exposed.pool")
data class ExposedPoolProperties(
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 2,
)
