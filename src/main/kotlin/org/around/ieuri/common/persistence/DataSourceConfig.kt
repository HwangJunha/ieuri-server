package org.around.ieuri.common.persistence

import org.springframework.boot.context.properties.ConfigurationProperties
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
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun dataSource(properties: DataSourceProperties): DataSource =
        properties.initializeDataSourceBuilder().build()
}
