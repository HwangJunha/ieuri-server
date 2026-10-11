package org.around.ieuri.common.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * 메인 DataSource·JdbcTemplate·Flyway. 검색 노드 빈이 있어 Boot 자동구성이 물러나므로 전부 명시한다.
 * - [dataSource]에만 `@Primary`가 필요하다 — 트랜잭션 매니저와 배치가 타입으로 받는다.
 * - 검색 노드로 가는 어댑터는 `searchJdbcTemplate`을 이름으로 지목한다.
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties::class, ReadReplicaProperties::class)
class DataSourceConfig {

    /** 쓰기(primary)용 풀. `@Primary`가 아니다 — 주입 대상은 [dataSource]의 라우팅 쪽이다. */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun primaryDataSource(properties: DataSourceProperties): DataSource = properties.initializeDataSourceBuilder().build()

    /**
     * 조회(replica)용 풀. `ieuri.read-replica.url`이 비면 null이다.
     * - 빈 문자열은 `@ConditionalOnProperty`로 거를 수 없어 여기서 가른다.
     * - primary와 같은 hikari 설정을 다시 적용한다 — search_path를 물려받지 못하면 생 SQL이 테이블을 못 찾는다.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun replicaDataSource(
        properties: DataSourceProperties,
        replica: ReadReplicaProperties,
    ): DataSource? = replica.url.takeIf { it.isNotBlank() }?.let { url ->
        properties.initializeDataSourceBuilder()
            .url(url)
            .build()
    }

    /**
     * 트랜잭션 매니저와 배치가 타입으로 주입받는 기본 DataSource.
     * - replica가 있으면 라우팅을 [LazyConnectionDataSourceProxy]로 감싼다. 감싸지 않으면 조용히 전부 primary로 간다.
     */
    @Bean
    @Primary
    fun dataSource(
        @Qualifier("primaryDataSource") primary: DataSource,
        @Qualifier("replicaDataSource") replica: DataSource?,
    ): DataSource {
        // 복제본 url이 비면 null이 주입된다 — 라우팅 없이 primary 하나만 쓴다.
        replica ?: return primary
        return LazyConnectionDataSourceProxy(ReadWriteRoutingDataSource(primary, replica))
    }

    /**
     * 메인 JdbcTemplate. 배치 JobRepository가 이 이름으로 쓴다. `searchJdbcTemplate`이 있어 자동구성이 물러나므로 여기서 명시한다.
     * - `@Primary`를 붙이지 않는다 — 주입 파라미터 이름으로 해소되며, 검색 노드 쪽이 이름을 빠뜨리면 기동이 깨지게 둔다.
     */
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

    /**
     * 메인 DB 마이그레이션. 검색 노드용 `searchFlyway`가 있어 자동구성이 물러나므로 여기서 명시한다.
     * 빠지면 메인 마이그레이션이 오류 없이 조용히 실행되지 않는다.
     * - `spring.flyway.*`를 읽어 자동구성과 동작을 맞춘다.
     * - [javaMigrations]를 넘겨야 한다. 빠뜨리면 빈으로 등록된 Java 마이그레이션이 해석되지 않아 검증이 깨진다.
     * - 트랜잭션 매니저보다 먼저 돈다([ExposedConfig.transactionManager]의 `@DependsOn`).
     * - Hikari 풀이 아니라 접속 정보를 직접 넘긴다(풀을 넘기면 정리 단계에서 닫힌 커넥션 스택트레이스가 찍힌다).
     */
    @Bean(initMethod = "migrate")
    fun flyway(
        dataSourceProperties: DataSourceProperties,
        properties: FlywayProperties,
        javaMigrations: ObjectProvider<JavaMigration>,
    ): Flyway = Flyway.configure()
        .dataSource(
            dataSourceProperties.url,
            dataSourceProperties.username,
            dataSourceProperties.password,
        )
        .locations(*properties.locations.toTypedArray())
        // `flyway_schema_history`가 `public`에 남게 못 박는다(SPEC-0023) — 기본 스키마가 바뀌면 V1부터 재적용하려 든다.
        // `schemas(...)`로 소유 스키마를 나열하지 않는다(없으면 만들고 clean에서 지운다).
        // 주의: hikari connection-init-sql이 여기 닿지 않는다 — 모든 마이그레이션은 스키마 한정으로 쓴다.
        .defaultSchema("public")
        .baselineOnMigrate(properties.baselineOnMigrate)
        .baselineVersion(properties.baselineVersion)
        .ignoreMigrationPatterns(*properties.ignoreMigrationPatterns.toTypedArray())
        .javaMigrations(*javaMigrations.orderedStream().toList().toTypedArray())
        .load()
}

/** `spring.flyway.*` 중 이 앱이 쓰는 값만. 자동구성이 물러나 Boot의 FlywayProperties가 등록되지 않는다. */
@ConfigurationProperties(prefix = "spring.flyway")
data class FlywayProperties(
    val locations: List<String> = listOf("classpath:db/migration"),
    val baselineOnMigrate: Boolean = true,
    val baselineVersion: String = "0",
    val ignoreMigrationPatterns: List<String> = listOf("repeatable:missing"),
)

/**
 * 읽기 복제본 접속. url이 비어 있으면 라우팅 없이 전부 primary로 간다. 계정은 primary와 같다.
 */
@ConfigurationProperties(prefix = "ieuri.read-replica")
data class ReadReplicaProperties(
    val url: String = "",
)
