package org.around.ieuri.tourism.infrastructure.config

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 검색 전용 노드 접속(SPEC-0013). **메인 DB와 다른 인스턴스다.**
 *
 * pg_search 커뮤니티 에디션은 물리 복제(standby)를 지원하지 않아, BM25 인덱스가 있는 DB는
 * HA 클러스터가 될 수 없다. 그래서 메인은 pg_search 없이 HA를 유지하고, 검색은 논리 복제로
 * 행만 받아오는 단일 노드가 담당한다. 그 노드가 여기 연결 대상이다.
 *
 * **빈을 조건부로 만들지 않는다.** 조건을 걸면 이 빈을 받는 어댑터·서비스·리졸버까지 조건이
 * 번져 계층마다 `@ConditionalOnProperty`가 붙는다. 접속 정보가 틀렸다면 그 사실이 검색
 * 쿼리에서 드러나는 편이 낫다 — 부팅은 되고 검색만 실패한다.
 */
@Configuration
@EnableConfigurationProperties(SearchDataSourceProperties::class)
class SearchDataSourceConfig {

    /**
     * 검색 노드 DataSource. **`@Primary`가 아니다** — 붙이면 JPA·배치·기존 어댑터가 전부
     * 검색 노드를 보게 된다. 검색 어댑터 3종만 [searchJdbcTemplate]을 통해 이 쪽을 쓴다.
     *
     * 이 빈이 존재하는 것만으로 Boot의 DataSource 자동구성이 물러나므로, 메인 DataSource는
     * `common/persistence/DataSourceConfig`가 명시적으로 선언한다.
     */
    @Bean
    fun searchDataSource(properties: SearchDataSourceProperties): DataSource =
        DataSourceBuilder.create()
            .type(HikariDataSource::class.java)
            .url(properties.url)
            .username(properties.username)
            .password(properties.password)
            .build()
            .apply {
                // 검색은 조회 전용이라 메인만큼 커넥션이 필요 없다.
                maximumPoolSize = properties.maxPoolSize
                poolName = "search-pool"
                // 메인 풀과 같은 이유로 기동 시 첫 커넥션을 재시도한다
                // (application.yaml의 spring.datasource.hikari 주석 참조).
                initializationFailTimeout = 30_000
                // 논리 복제 구독자에 쓰기가 가지 않게 커넥션 단에서 막는다 — 실수로 INSERT가
                // 나가면 복제가 충돌한다. DDL은 Flyway가 자체 커넥션으로 수행하므로 영향 없다.
                isReadOnly = true
                // 검색 어댑터의 raw SQL이 테이블을 스키마 한정 없이 부른다(SPEC-0023 R7).
                // 이 노드에는 `tour`만 있고, `public`은 PostGIS·pgvector가, `paradedb`는
                // pg_search가 산다 — BM25 연산자(`@@@`)와 `pdb.score()`가 거기서 해석되므로
                // 셋 다 남겨야 한다.
                connectionInitSql = "SET search_path TO tour, public, paradedb"
            }

    /**
     * 검색 노드 스키마를 이 앱이 소유한다. `db/search-node`는 메인의 `db/migration`과 **별도
     * 이력**(각자의 flyway_schema_history)이며, spot 관련 테이블과 BM25 인덱스만 담는다.
     *
     * 논리 복제는 행만 옮기고 DDL은 옮기지 않으므로, 구독이 붙기 전에 그릇이 있어야 한다.
     *
     * **`initMethod = "migrate"`로 부팅 시 실행한다.** Boot의 Flyway 자동구성은 기본
     * DataSource만 다루므로 이쪽은 직접 등록한다.
     */
    @Bean(initMethod = "migrate")
    fun searchFlyway(properties: SearchDataSourceProperties): Flyway =
        Flyway.configure()
            // **조회용 풀을 쓰지 않고 접속 정보를 직접 넘긴다.** 그 풀은 read-only라
            // (구독자에 실수로 쓰지 않기 위한 장치) DDL이 거부된다. Flyway는 부팅 시 한 번만
            // 돌고 끝나므로 자체 커넥션으로 충분하다.
            .dataSource(properties.url, properties.username, properties.password)
            .locations("classpath:db/search-node")
            // **flyway_schema_history를 `public`에 유지한다**(SPEC-0023). 테이블은 `tour`에
            // 있지만 이력 테이블은 따라가지 않는다 — 옮기면 기존 노드가 이력을 잃고 V1부터
            // 다시 적용하려 든다.
            //
            // `schemas("public", "tour")`로 적지 않는다 — 그 목록은 Flyway가 소유를 주장하는
            // 대상이라 없으면 만들고 `clean`에서 지운다. `tour`는 V1이 만든다.
            .defaultSchema("public")
            // 메인과 이력이 섞이지 않게 테이블 이름을 명시한다. 기본값과 같지만, 이 DB에
            // 두 이력이 공존할 여지를 남기지 않으려는 의도를 드러낸다.
            .table("flyway_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()

    /**
     * 검색 어댑터 3종이 쓰는 JdbcTemplate.
     *
     * **`@Qualifier`가 반드시 있어야 한다.** 파라미터 이름이 `searchDataSource`라도 주입은
     * 타입으로 먼저 해석되어 `@Primary`인 메인 DataSource가 들어온다. 그러면 검색 쿼리가
     * 메인으로 나가 `could not access file "pg_search"`로 실패한다(실측).
     */
    @Bean
    fun searchJdbcTemplate(@Qualifier("searchDataSource") searchDataSource: DataSource): JdbcTemplate =
        JdbcTemplate(searchDataSource)
}

/**
 * 검색 노드 접속 설정.
 *
 * @param url 비어 있으면 검색 노드 연결을 만들지 않는다(검색 기능이 꺼진다).
 * @param maxPoolSize 조회 전용이라 메인보다 작게 잡는다.
 */
@ConfigurationProperties(prefix = "ieuri.search-datasource")
data class SearchDataSourceProperties(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val maxPoolSize: Int = 5,
)
