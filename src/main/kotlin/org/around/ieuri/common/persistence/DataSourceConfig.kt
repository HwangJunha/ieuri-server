package org.around.ieuri.common.persistence

import org.flywaydb.core.Flyway
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * 메인 DataSource.
 *
 * **Boot 자동구성에 맡기지 못하고 여기서 명시한다.** 자동구성은
 * `@ConditionalOnMissingBean(DataSource)` 조건이라, 다른 곳에서 DataSource 빈을 하나라도
 * 만들면 통째로 물러난다. SPEC-0013이 검색 노드용 DataSource를 추가하면서 그 일이 실제로
 * 벌어졌고, JPA가 검색 노드를 붙잡아 `Unable to determine Dialect`로 기동이 깨졌다.
 *
 * `@Primary`이므로 JPA·배치·기존 어댑터가 전부 이 쪽을 쓴다. 검색 노드로 가야 하는 것은
 * 검색 어댑터 3종뿐이며, 그것들은 `searchJdbcTemplate`을 이름으로 지목한다
 * (tourism의 `SearchDataSourceConfig`).
 *
 * 도메인이 아니라 `common`에 두는 이유는 이 빈이 전 도메인의 기본 연결이기 때문이다 —
 * tourism 설정에 두면 tourism이 다른 도메인의 영속성을 쥐는 모양이 된다.
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties::class)
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun dataSource(properties: DataSourceProperties): DataSource =
        properties.initializeDataSourceBuilder().build()

    /**
     * 메인 DB 마이그레이션. **DataSource와 똑같은 이유로 여기서 명시한다.**
     *
     * Boot의 Flyway 자동구성은 `@ConditionalOnMissingBean(Flyway)`라, 다른 곳에서 Flyway 빈을
     * 하나라도 만들면 통째로 물러난다. SPEC-0013이 검색 노드용 `searchFlyway`를 추가하면서
     * 그 일이 벌어졌고, **그때부터 메인 DB 마이그레이션이 조용히 실행되지 않았다** —
     * 기동은 정상이고 로그에도 아무 말이 없어서, 새 마이그레이션(V43)을 추가하고서야 드러났다.
     * 위 [dataSource]가 겪은 것과 같은 함정이며, 그쪽은 기동이 깨져 바로 보였을 뿐이다.
     *
     * `initMethod = "migrate"`로 기동 시 실행한다. 설정은 `spring.flyway.*`를 그대로 읽어
     * 자동구성과 동작을 맞춘다 — `baseline-on-migrate`·`baseline-version`·
     * `ignore-migration-patterns`가 거기 있고, 그 의미는 application.yaml의 주석이 설명한다.
     *
     * **JPA보다 먼저 돌아야 한다.** `entityManagerFactory`가 이 빈에 의존하도록 Boot가
     * 엮어 주므로(`FlywaySchemaManagementProvider`), `ddl-auto: validate`는 마이그레이션이
     * 끝난 스키마를 검증한다.
     */
    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource, properties: FlywayProperties): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations(*properties.locations.toTypedArray())
            .baselineOnMigrate(properties.baselineOnMigrate)
            .baselineVersion(properties.baselineVersion)
            .ignoreMigrationPatterns(*properties.ignoreMigrationPatterns.toTypedArray())
            .load()
}

/**
 * `spring.flyway.*` 중 이 앱이 실제로 쓰는 값만. Boot의 `FlywayProperties`를 그대로 주입받지
 * 않는 이유는 자동구성이 물러난 상태라 그 클래스가 등록되지 않기 때문이다.
 */
@ConfigurationProperties(prefix = "spring.flyway")
data class FlywayProperties(
    val locations: List<String> = listOf("classpath:db/migration"),
    val baselineOnMigrate: Boolean = true,
    val baselineVersion: String = "0",
    val ignoreMigrationPatterns: List<String> = listOf("repeatable:missing"),
)
