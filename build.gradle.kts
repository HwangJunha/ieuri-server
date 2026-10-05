plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    // QueryDSL Q클래스 생성을 위한 애노테이션 프로세싱(kapt).
    kotlin("kapt") version "2.3.21"
    // 스키마 우선: schema/*.graphqls로부터 GraphQL input/output/enum 타입을 생성한다.
    id("com.netflix.dgs.codegen") version "8.7.0"
    // 스타일 센서(ktlint). `check`에 lintKotlin이 붙는다 — 규칙은 .editorconfig.
    id("org.jmailen.kotlinter") version "5.7.0"
}

group = "org.around"
version = "0.0.1"
description = "ieuri"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["netflixDgsVersion"] = "12.1.0"
// DGS 12가 strictly 3.0.0을 요구한다 — 아래 Boot BOM 재import가 2.10.0으로 내리지 않게 Boot 버전 속성으로 맞춘다.
extra["json-path.version"] = "3.0.0"
extra["exposedVersion"] = "1.5.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 영속성 이전 대상(SPEC-0039 D-32). DSL만 쓴다 — DAO 모듈은 넣지 않는다.
    // 스타터가 아니라 트랜잭션 모듈만 쓴다: 스타터의 자동구성이 트랜잭션 매니저를 만들면 JPA 매니저 자동구성이 물러난다.
    implementation("org.jetbrains.exposed:exposed-core:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-java-time:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:spring7-transaction:${property("exposedVersion")}")
    // 기동 시 스키마 검증(D-35).
    implementation("org.jetbrains.exposed:exposed-migration-core:${property("exposedVersion")}")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:${property("exposedVersion")}")
    // k8s 프로브용 health와 메트릭 노출. 노출 범위는 application.yaml의 management 블록이 정한다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 이 의존을 빼면 /actuator/prometheus가 404가 된다(management 설정과 무관하게).
    implementation("io.micrometer:micrometer-registry-prometheus")
    // 트레이싱. 라이브러리(bridge-otel·exporter-otlp)가 아니라 이 스타터여야 한다 —
    // 빼고 라이브러리만 두면 앱은 정상으로 뜨는데 span이 나가지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // JDBC 계측 — 쿼리마다 span을 만든다(자동으로 붙지 않는다).
    // 2.3.0 미만은 readOnly 설정 전에 커넥션을 확정해 읽기 복제본 라우팅을 조용히 무력화한다(업스트림 #114).
    implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:2.3.0")
    // refresh 토큰 저장소(Valkey, Redis 프로토콜 호환). Lettuce 클라이언트.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // @RequiresPermission 선언적 인가(애스펙트)용 AspectJ 위버. 프록시 활성화는 AopConfig.
    implementation("org.aspectj:aspectjweaver")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // RestClient.Builder 자동구성. Boot 4는 이 모듈을 명시해야 자동구성 빌더가 생긴다.
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // 배치의 동시 upstream 호출(SPEC-0037). 예외를 ExecutionException으로 감싸지 않는다.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // TourAPI 호출의 커넥션 풀. 전이 의존에 기대지 않는다 — 빠지면 조용히 SimpleClientHttpRequestFactory로 돌아간다.
    implementation("org.apache.httpcomponents.client5:httpclient5")
    // TourAPI 초당 호출 제한(SPEC-0037). 세마포어(동시 실행 수)로는 초당 호출 수를 묶을 수 없다.
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.3.0")
    // 읽기(CQRS) 프로젝션: QueryDSL. Hibernate 7과 호환되는 OpenFeign 포크(jakarta).
    implementation("io.github.openfeign.querydsl:querydsl-jpa:6.11")
    kapt("io.github.openfeign.querydsl:querydsl-apt:6.11:jakarta")
    kapt("jakarta.persistence:jakarta.persistence-api")
    kapt("jakarta.annotation:jakarta.annotation-api")
    // 관광지 동기화 등 배치 잡(SPEC-0004). 메타 테이블(BATCH_*)은 Flyway가 소유한다.
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    // Cloudflare R2(S3 호환) presigned URL 발급과 오브젝트 조작(SPEC-0010). S3Presigner도 이 아티팩트에 있다.
    implementation("software.amazon.awssdk:s3:2.31.77")
    // 자체 발급 HS256 JWT. api만 컴파일 의존, 구현/직렬화는 런타임.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // 외부 SNS id_token(RS256/ES256) 서명 검증 + JWKS 원격 조회/캐시. jjwt는 JWKS 조회를 지원하지 않는다.
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    // FCM 앱 푸시 발송(SPEC-0028, POL-027). 끌고 오는 Jackson 2는 Jackson 3(tools.jackson)와 좌표가 달라 공존한다.
    implementation("com.google.firebase:firebase-admin:9.4.3")
    // 애플 revoke용 .p8(PEM) 개인키 파싱. nimbus의 PEM 파서는 공개키를 함께 요구해 쓸 수 없다.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("io.mockk:mockk:1.13.13")
    // 패키지 의존성 방향(infrastructure → application → domain) 검증
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    // 소스 수준 컨벤션(와일드카드 import) 검사. ArchUnit은 바이트코드라 import 형태를 볼 수 없다.
    // Konsist는 자체 임베디드 Kotlin 컴파일러로 파싱한다 — Kotlin을 올릴 때 이쪽이 먼저 깨질 수 있다.
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:${property("netflixDgsVersion")}")
        // Boot BOM을 뒤에 한 번 더 import한다 — 나중 import가 이긴다. 빼면 DGS BOM이 reactor-core 등을 Boot보다 낮게 덮어쓴다.
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
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

// schema/*.graphqls → Kotlin GraphQL 타입 생성(서버 전용). 스키마 전체의 공유 커널이라 common 하위에 둔다.
tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask> {
    schemaPaths = mutableListOf("${projectDir}/src/main/resources/schema")
    packageName = "org.around.ieuri.common.graphql.generated"
    generateClient = false
    language = "kotlin"
    // GraphQL ID 스칼라를 Long으로 생성한다 — 형식이 틀린 id는 리졸버 전에 GraphQL이 거부한다.
    typeMapping = mutableMapOf("ID" to "kotlin.Long")
}

// 생성된 GraphQL 타입을 소스 디렉터리로 등록한다 — 빼도 빌드는 되지만 IDE가 생성 타입을 미해결로 표시한다.
sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/dgs-codegen"))
    }
}

// 컴파일 전에 코드 생성이 끝나 있도록 한다.
// IDE에서 생성 타입이 미해결로 보이면 `./gradlew generateJava` 후 Gradle sync를 한다.
tasks.named("compileKotlin") {
    dependsOn(tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask>())
}

// 자동 생성 코드는 스타일 검사 대상이 아니다 — 위 srcDir 등록 때문에 main 소스셋에 들어온다.
tasks.withType<org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask>().configureEach {
    val generatedDir = layout.buildDirectory.dir("generated").get().asFile
    exclude { it.file.startsWith(generatedDir) }
}

// plain jar는 끈다 — Main-Class 없는 jar가 build/libs에 생겨 이미지에 잘못 실릴 수 있다.
tasks.jar { enabled = false }

// 파일명을 고정해 버전이 바뀌어도 Dockerfile을 손대지 않게 한다. 배포 식별은 이미지 태그가 맡는다.
tasks.bootJar { archiveFileName.set("app.jar") }

