plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.0"
    // QueryDSL Q클래스 생성을 위한 애노테이션 프로세싱(kapt).
    kotlin("kapt") version "2.3.0"
    // 스키마 우선(schema-first): auth.graphqls로부터 GraphQL input/output/enum 타입을 생성한다.
    id("com.netflix.dgs.codegen") version "8.6.0"
}

group = "org.around"
version = "0.0.1-SNAPSHOT"
description = "ieuri"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["netflixDgsVersion"] = "11.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // refresh 토큰 저장소를 Valkey(Redis 프로토콜 호환)로 이관. Lettuce 클라이언트 기본.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // @RequiresPermission 선언적 인가(애스펙트)용 AspectJ 위버. spring-aop는 전이 의존이나,
    // 위버는 명시해 두어 프록시 파싱(@Aspect) 요건을 드러낸다. 프록시 활성화는 @EnableAspectJAutoProxy(AopConfig).
    implementation("org.aspectj:aspectjweaver")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Spring Boot 4는 자동구성을 모듈로 분리했다. RestClient.Builder 자동구성(RestClientAutoConfiguration)은
    // 이 모듈에 있으므로, TourAPI 호출용 RestClient 빈을 자동구성 빌더로 만들려면 명시적으로 추가해야 한다.
    // (Flyway 자동구성을 spring-boot-flyway로 별도 추가하는 것과 동일한 패턴.)
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // 읽기(CQRS) 프로젝션: QueryDSL. Spring Boot 4(Hibernate 7)와 호환되는 OpenFeign 포크(jakarta) 사용.
    implementation("io.github.openfeign.querydsl:querydsl-jpa:6.11")
    kapt("io.github.openfeign.querydsl:querydsl-apt:6.11:jakarta")
    kapt("jakarta.persistence:jakarta.persistence-api")
    kapt("jakarta.annotation:jakarta.annotation-api")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Spring Boot 4는 자동구성을 모듈로 분리했다. Flyway 자동구성(FlywayAutoConfiguration)은
    // flyway-core가 아니라 이 모듈에 있으므로 명시적으로 추가해야 마이그레이션이 부팅 시 실행된다.
    implementation("org.springframework.boot:spring-boot-flyway")
    // JWT (JVM 표준 라이브러리 jjwt). api만 컴파일 의존, 구현/직렬화는 런타임.
    // 자체 발급 HS256 토큰 전용.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // 외부 SNS id_token(RS256/ES256) 서명 검증 + JWKS 원격 조회/캐시. jjwt는 JWKS 조회를
    // 지원하지 않아 별도 라이브러리를 쓴다. Google/Apple/Kakao 공개키로 서명·iss·aud·exp 검증.
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("io.mockk:mockk:1.13.13")
    // 패키지 의존성 방향(infrastructure → application → domain) 검증
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:${property("netflixDgsVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// schema/*.graphqls → Kotlin GraphQL 타입 생성(입력/출력/enum). 서버 전용이라 client 코드는 생성하지 않는다.
// 생성 타입은 특정 도메인 소유가 아니라 스키마 전체의 공유 커널이므로 common 하위에 둔다(도메인 간 격리 규칙 준수).
tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask> {
    schemaPaths = mutableListOf("${projectDir}/src/main/resources/schema")
    packageName = "org.around.ieuri.common.graphql.generated"
    generateClient = false
    language = "kotlin"
}
