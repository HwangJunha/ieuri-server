package org.around.ieuri.tourism.adapter.config

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
/**
 * 검색 전용 노드 접속(SPEC-0013). 메인 DB와 다른 인스턴스다(pg_search, 논리 복제 구독자).
 * - 빈을 조건부로 만들지 않는다 — 접속 정보가 틀리면 부팅은 되고 검색만 실패한다.
 */
@Configuration
@EnableConfigurationProperties(SearchDataSourceProperties::class)
class SearchDataSourceConfig {

    /**
     * 검색 노드 DataSource. `@Primary`가 아니다 — 붙이면 JPA·배치가 전부 검색 노드를 본다.
     * - 이 빈이 있으면 Boot의 DataSource 자동구성이 물러나므로 메인은 `common/persistence/DataSourceConfig`가 선언한다.
     */
    @Bean
    fun searchDataSource(properties: SearchDataSourceProperties): DataSource = DataSourceBuilder.create()
        .type(HikariDataSource::class.java)
        .url(properties.url)
        .username(properties.username)
        .password(properties.password)
        .build()
        .apply {
            // 검색은 조회 전용이라 메인만큼 커넥션이 필요 없다.
            maximumPoolSize = properties.maxPoolSize
            poolName = "search-pool"
            // 기동 시 첫 커넥션을 재시도한다(메인 풀과 같음).
            initializationFailTimeout = 30_000
            // 논리 복제 구독자에 쓰기가 가지 않게 막는다 — INSERT가 나가면 복제가 충돌한다.
            isReadOnly = true
            // raw SQL이 스키마 한정 없이 부른다(SPEC-0023 R7). `paradedb`가 빠지면 `@@@`·`pdb.score()`가 해석되지 않는다.
            connectionInitSql = "SET search_path TO tour, public, paradedb"
        }

    /**
     * 검색 노드 스키마 마이그레이션(`db/search-node`, 메인과 별도 이력). 부팅 시 실행한다.
     * - 논리 복제는 DDL을 옮기지 않으므로 구독이 붙기 전에 테이블이 있어야 한다.
     */
    @Bean(initMethod = "migrate")
    fun searchFlyway(properties: SearchDataSourceProperties): Flyway = Flyway.configure()
        // 조회용 풀은 read-only라 DDL이 거부된다 — 접속 정보를 직접 넘긴다.
        .dataSource(properties.url, properties.username, properties.password)
        .locations("classpath:db/search-node")
        // 이력 테이블은 `public`에 둔다(SPEC-0023) — 옮기면 기존 노드가 V1부터 다시 적용하려 든다.
        // `schemas(...)`로 적지 않는다 — 그 목록은 Flyway가 소유를 주장해 지우는 대상이 된다.
        .defaultSchema("public")
        // 메인과 이력이 섞이지 않게 테이블 이름을 명시한다.
        .table("flyway_schema_history")
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()

    /** 검색 어댑터가 쓰는 JdbcTemplate. `@Qualifier`가 빠지면 `@Primary`인 메인 DataSource가 주입된다. */
    @Bean
    fun searchJdbcTemplate(@Qualifier("searchDataSource") searchDataSource: DataSource): JdbcTemplate = JdbcTemplate(searchDataSource)
}
