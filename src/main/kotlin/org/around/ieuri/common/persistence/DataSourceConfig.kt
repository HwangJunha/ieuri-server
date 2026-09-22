package org.around.ieuri.common.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean
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
 * 메인 DataSource.
 *
 * **Boot 자동구성에 맡기지 못하고 여기서 명시한다.** 자동구성은
 * `@ConditionalOnMissingBean(DataSource)` 조건이라, 다른 곳에서 DataSource 빈을 하나라도
 * 만들면 통째로 물러난다. SPEC-0013이 검색 노드용 DataSource를 추가하면서 그 일이 실제로
 * 벌어졌고, JPA가 검색 노드를 붙잡아 `Unable to determine Dialect`로 기동이 깨졌다.
 *
 * **[dataSource]에 `@Primary`가 반드시 필요하다.** JPA·배치 자동구성은 `DataSource`를
 * **타입으로** 받는데 후보가 여럿(primary·replica·검색 노드)이라 기본값이 없으면 고르지
 * 못한다. 실제로 떼어 보면 `entityManagerFactory`가 아예 만들어지지 않아 기동이 깨진다(실측):
 *   `Parameter 0 of constructor in MemberPersistenceAdapter required a bean named
 *    'entityManagerFactory' that could not be found`
 * 우리 코드는 이름으로 지목할 수 있지만 프레임워크 코드는 그럴 수 없다 — 그래서 거기만
 * `@Primary`를 남긴다([jdbcTemplate]은 전부 우리 코드가 받으므로 붙이지 않는다).
 *
 * **읽기 복제본 라우팅이 [dataSource]에 있다**(PERF-20260921). `ieuri.read-replica.url`이
 * 있으면 읽기 전용 트랜잭션이 replica로 가고, 없으면 primary 하나만 쓴다.
 *
 * 검색 노드로 가야 하는 것은 검색 어댑터 3종뿐이며, 그것들은 `searchJdbcTemplate`을
 * 이름으로 지목한다(tourism의 `SearchDataSourceConfig`).
 *
 * 도메인이 아니라 `common`에 두는 이유는 이 빈이 전 도메인의 기본 연결이기 때문이다 —
 * tourism 설정에 두면 tourism이 다른 도메인의 영속성을 쥐는 모양이 된다.
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties::class, ReadReplicaProperties::class)
class DataSourceConfig {

    /**
     * 쓰기(primary)용 풀. 이 빈만 `spring.datasource.hikari.*`를 직접 받는다.
     *
     * **`@Primary`가 아니다** — 주입 대상은 [dataSource]가 돌려주는 라우팅 쪽이어야 한다.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun primaryDataSource(properties: DataSourceProperties): DataSource =
        properties.initializeDataSourceBuilder().build()

    /**
     * 조회(replica)용 풀(PERF-20260921). `ieuri.read-replica.url`이 비면 만들지 않는다 —
     * 그러면 [dataSource]가 라우팅 없이 primary 하나를 그대로 쓴다(로컬·배치의 기본값).
     *
     * **빈 문자열을 `@ConditionalOnProperty`로 거를 수 없어 조건이 아니라 여기서 가른다.**
     * 기본값이 `${IEURI_DB_RO_URL:}`이라 변수가 없어도 속성은 `""`로 **존재하고**,
     * 그 조건은 "속성이 있는가"만 보므로 `matchIfMissing = false`가 걸리지 않는다. 그대로
     * 두면 빈 URL로 풀을 만들어 `jdbcUrl is required with driverClassName`으로 기동이 깨진다.
     *
     * **접속 정보만 replica로 바꾸고 나머지 Hikari 설정은 primary와 같아야 한다.**
     * 특히 `connection-init-sql`의 `search_path`를 물려받지 못하면 생 SQL 어댑터가 테이블을
     * 찾지 못한다(SPEC-0023). 그래서 같은 `@ConfigurationProperties`를 다시 적용한다.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun replicaDataSource(
        properties: DataSourceProperties,
        replica: ReadReplicaProperties,
    ): DataSource? =
        replica.url.takeIf { it.isNotBlank() }?.let { url ->
            properties.initializeDataSourceBuilder()
                .url(url)
                .build()
        }

    /**
     * JPA·배치가 타입으로 주입받는 기본 DataSource.
     *
     * replica 풀이 있으면 [ReadWriteRoutingDataSource]를 **[LazyConnectionDataSourceProxy]로
     * 감싸** 돌려준다. 감싸지 않으면 트랜잭션 시작 시점에 커넥션을 먼저 잡아 라우팅 판단
     * 전에 primary가 정해진다 — 오류 없이 조용히 전부 primary로 간다.
     */
    @Bean
    @Primary
    fun dataSource(
        @Qualifier("primaryDataSource") primary: DataSource,
        beanFactory: ListableBeanFactory,
    ): DataSource {
        // 이름으로 찾는다 — 타입으로 받으면 primary 자신과 검색 노드까지 후보가 된다.
        //
        // **`replicaDataSource`는 URL이 비면 null을 돌려준다.** 그때 Spring은 그 이름을
        // `NullBean`으로 등록하므로 `containsBean`은 참이고 `getBean`은 실패한다 —
        // 있는지 없는지는 타입으로 조회되는지로 가른다.
        val replica = beanFactory.getBeanNamesForType(DataSource::class.java)
            .firstOrNull { it == REPLICA_BEAN }
            ?.let { beanFactory.getBean(it, DataSource::class.java) }
            ?: return primary
        return LazyConnectionDataSourceProxy(ReadWriteRoutingDataSource(primary, replica))
    }

    /**
     * 메인 JdbcTemplate. **[dataSource]·[flyway]와 똑같은 이유로 여기서 명시한다.**
     *
     * Boot의 자동구성은 `@ConditionalOnMissingBean(JdbcOperations)`라, `searchJdbcTemplate`이
     * 있으면 물러난다. 그 결과 `JdbcTemplate`을 타입으로 주입받는 생 SQL 어댑터가 전부
     * **검색 노드**를 보게 됐다 — 검색 노드에 `spot`은 있어서 반경 조회는 우연히 동작했고,
     * `congestion_area`가 없어 `seoulCongestionAreas`에서야 드러났다.
     *
     * **`@Primary`를 붙이지 않는다.** 후보가 둘이어도 주입 파라미터 이름이 `jdbcTemplate`이라
     * 빈 이름으로 해소된다(Spring의 by-type 동점 처리). `@Primary`는 "이름을 안 써도 이게
     * 기본"이라는 뜻이라, 검색 노드를 써야 할 곳이 이름을 빠뜨렸을 때 조용히 메인으로
     * 흘려보낸다 — 그 실수는 오류가 아니라 **잘못된 DB 조회**로 나타나 알아채기 어렵다.
     * 지금은 두 후보 중 하나를 반드시 이름으로 고르게 되어 있고, 이름이 틀리면 기동이 깨진다.
     *
     * 검색 노드로 가야 하는 어댑터 3종은 `searchJdbcTemplate`을 `@Qualifier`로 지목한다.
     */
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

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
     * **[javaMigrations]를 넘긴다.** 자동구성이라면 `JavaMigration` 빈을 모아 Flyway에
     * 넘겨 주는데, 이 설정이 자동구성을 물러나게 했으므로 그 일을 대신한다. 빠뜨리면
     * 의존성을 주입받는 Java 마이그레이션(pepper가 필요해 SQL로 쓸 수 없는 것들)이
     * 클래스패스 스캔으로 만들어지지 않아, Flyway가 이미 적용된 버전을 로컬에서 못 찾고
     * `Detected applied migration not resolved locally`로 검증이 깨진다.
     *
     * **지금 `JavaMigration` 빈은 하나도 없다** — 유일했던 것이 베이스라인 스쿼시로
     * 접혔다. 배선을 남기는 이유는 다음에 하나라도 생기는 순간 위 고장이 되살아나고,
     * 그때 원인이 이 파일이라는 것을 알아보기 어렵기 때문이다.
     *
     * **JPA보다 먼저 돌아야 한다.** `ddl-auto: validate`가 마이그레이션이 끝난 스키마를
     * 봐야 하므로 [entityManagerFactoryDependsOnFlyway]로 순서를 명시한다 — 자동구성이
     * 물러난 상태라 Boot의 `FlywaySchemaManagementProvider`가 대신 엮어 주지 않는다.
     *
     * **Hikari 풀을 쓰지 않고 접속 정보를 직접 넘긴다**(`searchFlyway`와 같은 방식).
     * 풀을 넘기면 Flyway가 마이그레이션을 마치고 커넥션을 반납한 뒤, 정리 단계에서
     * `restoreOriginalSchema`로 그 커넥션을 다시 만지다가 `This connection has been closed`
     * 스택트레이스를 남긴다. 마이그레이션 자체는 이미 끝난 뒤라 무해하지만 기동할 때마다
     * 찍힌다. Flyway는 부팅 시 한 번 돌고 끝나므로 자체 커넥션으로 충분하다.
     */
    @Bean(initMethod = "migrate")
    fun flyway(
        dataSourceProperties: DataSourceProperties,
        properties: FlywayProperties,
        javaMigrations: ObjectProvider<JavaMigration>,
    ): Flyway =
        Flyway.configure()
            .dataSource(
                dataSourceProperties.url,
                dataSourceProperties.username,
                dataSourceProperties.password,
            )
            .locations(*properties.locations.toTypedArray())
            // **`flyway_schema_history`가 `public`에 남게 못 박는다**(SPEC-0023).
            // 지정하지 않으면 Flyway는 커넥션의 기본 스키마에서 이력을 찾는데, 지금 그것이
            // `public`인 것은 우연이다 — 누가 롤이나 DB의 search_path를 바꾸면 이력을 찾지
            // 못하고 **V1부터 다시 적용하려 든다.** 테이블이 이미 있는 DB에서는 통째로 실패한다.
            //
            // V62는 이력 테이블을 옮기지 않는다. 옮기면 기존 환경이 이력을 잃는다.
            //
            // **`schemas(...)`로 5개를 나열하지 않는다.** 그 목록은 Flyway가 소유를 주장하는
            // 대상이라 없으면 만들고 `clean`에서 지운다 — 스키마 생성은 V1 베이스라인과 CNPG
            // 매니페스트가 이미 나눠 갖고 있으므로 소유자를 셋으로 늘릴 이유가 없다.
            //
            // **주의: `spring.datasource.hikari.connection-init-sql`이 여기 닿지 않는다.**
            // 위에 적었듯 Flyway는 풀을 쓰지 않고 자체 커넥션을 만들기 때문이다. 그래서
            // **모든 마이그레이션은 스키마 한정으로 써야 한다** — `ALTER TABLE community_post`가
            // 아니라 `ALTER TABLE community.community_post`.
            .defaultSchema("public")
            .baselineOnMigrate(properties.baselineOnMigrate)
            .baselineVersion(properties.baselineVersion)
            .ignoreMigrationPatterns(*properties.ignoreMigrationPatterns.toTypedArray())
            .javaMigrations(*javaMigrations.orderedStream().toList().toTypedArray())
            .load()

    /**
     * `entityManagerFactory`가 [flyway] **뒤에** 만들어지도록 강제한다.
     *
     * 자동구성이 살아 있을 때는 Boot가 `FlywaySchemaManagementProvider`로 이 순서를 엮어
     * 주는데, `searchFlyway` 때문에 자동구성이 물러나 그 장치가 없다. 순서가 없으면
     * Hibernate의 `ddl-auto: validate`가 마이그레이션 **전** 스키마를 검증할 수 있고,
     * 그때 새 테이블이 없다며 기동이 깨진다(V43 같은 신규 마이그레이션에서 드러난다).
     */
    companion object {
        /** 읽기 복제본 풀의 빈 이름. [dataSource]가 이름으로 지목한다. */
        private const val REPLICA_BEAN = "replicaDataSource"

        /**
         * **`@JvmStatic`이어야 한다.** BeanFactoryPostProcessor는 컨테이너 생명주기의 아주
         * 이른 시점에 만들어지는데, 인스턴스 메서드로 두면 그것을 담은 @Configuration이
         * 너무 일찍 초기화되어 Spring이 경고를 낸다.
         */
        @Bean
        @JvmStatic
        fun entityManagerFactoryDependsOnFlyway(): BeanFactoryPostProcessor =
            BeanFactoryPostProcessor { beanFactory ->
                for (name in beanFactory.getBeanNamesForType(AbstractEntityManagerFactoryBean::class.java, true, false)) {
                    // FactoryBean 조회는 `&`가 붙은 이름을 주는데 빈 정의는 그 접두사 없이 등록돼 있다.
                    val definitionName = BeanFactoryUtils.transformedBeanName(name)
                    val definition = beanFactory.getBeanDefinition(definitionName)
                    definition.setDependsOn(*((definition.dependsOn ?: emptyArray()) + "flyway"))
                }
            }
    }
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

/**
 * 읽기 복제본 접속(PERF-20260921).
 *
 * @param url 비어 있으면 라우팅을 만들지 않는다 — 전부 primary로 간다(로컬·배치의 기본값).
 *            계정은 primary와 같으므로 `spring.datasource.username/password`를 그대로 쓴다.
 */
@ConfigurationProperties(prefix = "ieuri.read-replica")
data class ReadReplicaProperties(
    val url: String = "",
)
