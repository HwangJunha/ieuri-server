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

extra["netflixDgsVersion"] = "11.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // k8s readiness·liveness 프로브용 health 엔드포인트와 메트릭 노출.
    // 무엇을 열고 무엇을 닫는지는 application.yaml의 management 블록이 정한다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // **이 의존이 있어야 /actuator/prometheus가 생긴다.** actuator는 레지스트리 구현이
    // 클래스패스에 있을 때만 그 엔드포인트를 자동구성하므로, 빼면 management 설정에
    // prometheus를 적어 두어도 404가 되고 원인이 설정 쪽으로 보여 찾기 어렵다.
    implementation("io.micrometer:micrometer-registry-prometheus")
    // 트레이싱. 메트릭이 집계된 숫자라면 트레이스는 **요청 하나를 끝까지 따라가는 것**이다.
    // 느린 요청이 어디서 느렸는지(리졸버냐 JPA 쿼리냐)는 집계로는 알 수 없다 — 히스토그램의
    // 마지막 버킷에 1이 더해질 뿐이다.
    //
    // **라이브러리가 아니라 이 스타터를 넣어야 한다.** Boot 4는 자동구성을 모듈로 분리했고
    // (spring-boot-restclient와 같은 패턴) 트레이싱 자동구성이 이 스타터에 있다.
    // micrometer-tracing-bridge-otel과 opentelemetry-exporter-otlp만 넣으면 클래스는
    // 있으나 자동구성이 없어 `management.opentelemetry.tracing.export.otlp.*` 속성이
    // 바인딩되지 않는다 — 앱은 정상으로 뜨고 요청도 처리되는데 span만 나가지 않는다.
    // 두 라이브러리는 이 스타터가 전이로 가져오므로 따로 적지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // **JDBC 계측은 자동으로 붙지 않는다.** Lettuce(Valkey)는 자체 Micrometer 관측을 가져
    // Redis 호출이 span 에 잡히지만, JDBC 에는 그런 것이 없어 SQL 이 트레이스에서 빠진다 —
    // "GraphQL 요청이 느렸다"까지만 알고 어느 쿼리가 느렸는지는 알 수 없다. 2026-08-30 의
    // 125초 요청이 정확히 그 질문이었다.
    //
    // 쿼리마다 span 이 생기므로 N+1 이 있는 요청은 span 이 수십 개가 된다. 개발은 표본이
    // 전량이지만 호출이 잦지 않고, 운영은 10% 라 감당 가능하다고 보았다. 용량이 문제되면
    // 표본 비율을 먼저 조정한다.
    //
    // 2.x 가 Spring Boot 4 계열이다(POM 이 spring-boot-autoconfigure 4.0.5 를 참조).
    //
    // **2.3.0 미만은 읽기 복제본 라우팅을 조용히 무력화한다**(PERF-20260921, 업스트림 #114).
    // 계측이 span 에 url/host/port 를 채우려고 커넥션을 받자마자 `getMetaData()` 를 부르는데,
    // 그 시점은 Spring 이 `Connection#setReadOnly(true)` 를 걸기 **전**이다. 그래서
    // `LazyConnectionDataSourceProxy` 가 지연해 두려던 실제 커넥션이 거기서 확정되고,
    // readOnly 가 아직 false 라 **전부 primary 로 간다** — 오류도 로그도 없다.
    // 2.3.0 이 `JdbcConnectionInfo` 를 DataSource 단위로 캐시해(업스트림 #119) 이 조기 호출을 없앴다.
    implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:2.3.0")
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
    // 배치의 동시 upstream 호출(SPEC-0037). Future 대신 쓰는 이유는 **예외가 감싸지지 않는
    // 것**이다 — Future.get()은 ExecutionException으로 덮어 일일 한도와 장애의 구분을 호출자가
    // 놓치게 만들고, 그것을 벗기는 코드가 매번 따라붙는다. 호출 속도는 RateLimiter가 잡는다.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // TourAPI 호출의 커넥션 풀(SPEC-0037 후속). **전이 의존성에 기대지 않는다** — DGS가 떨구면
    // 조용히 `SimpleClientHttpRequestFactory` 동작으로 돌아가고, 그 결함은 12분쯤 뒤에야 드러난다.
    implementation("org.apache.httpcomponents.client5:httpclient5")
    // TourAPI 초당 호출 제한(SPEC-0037). **세마포어로는 표현할 수 없다** — 동시 실행 수를 묶어도
    // 응답이 빠르면 초당 호출이 얼마든 늘어난다(실측: 동시 20으로 1000건이 2초에 나갔다).
    // 한도는 (언어 × 오퍼레이션) 조합마다 초당 50이며, 여기서 제한하는 것은 초당 호출 수다.
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.3.0")
    // 읽기(CQRS) 프로젝션: QueryDSL. Spring Boot 4(Hibernate 7)와 호환되는 OpenFeign 포크(jakarta) 사용.
    implementation("io.github.openfeign.querydsl:querydsl-jpa:6.11")
    kapt("io.github.openfeign.querydsl:querydsl-apt:6.11:jakarta")
    kapt("jakarta.persistence:jakarta.persistence-api")
    kapt("jakarta.annotation:jakarta.annotation-api")
    // 관광지 목록 일 1회 동기화 잡(SPEC-0004). 메타 테이블(BATCH_*)은 Flyway 마이그레이션으로 소유하고
    // spring.batch.jdbc.initialize-schema=never로 둔다 — 스키마 형상 관리를 Flyway로 일원화하기 위함.
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Spring Boot 4는 자동구성을 모듈로 분리했다. Flyway 자동구성(FlywayAutoConfiguration)은
    // flyway-core가 아니라 이 모듈에 있으므로 명시적으로 추가해야 마이그레이션이 부팅 시 실행된다.
    implementation("org.springframework.boot:spring-boot-flyway")
    // Cloudflare R2(S3 호환) presigned URL 발급과 확인 단계의 오브젝트 조작(SPEC-0010 R18·R19).
    // 서명을 손으로 만들지 않는다 — SigV4는 정본 요청 문자열 한 글자만 어긋나도 조용히 실패하고,
    // Content-Type·Content-Length를 서명 항목에 넣는 일까지 SDK가 네이티브로 지원한다.
    // S3Presigner도 이 아티팩트에 들어 있다 — 별도의 s3-presigner 모듈은 존재하지 않는다.
    implementation("software.amazon.awssdk:s3:2.31.77")
    // JWT (JVM 표준 라이브러리 jjwt). api만 컴파일 의존, 구현/직렬화는 런타임.
    // 자체 발급 HS256 토큰 전용.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // 외부 SNS id_token(RS256/ES256) 서명 검증 + JWKS 원격 조회/캐시. jjwt는 JWKS 조회를
    // 지원하지 않아 별도 라이브러리를 쓴다. Google/Apple/Kakao 공개키로 서명·iss·aud·exp 검증.
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    // FCM 앱 푸시 발송(SPEC-0028, POL-027). sendEach()가 토큰별 성공/실패와 MessagingErrorCode를
    // 돌려주므로 무효 토큰만 골라 지우는 R4를 응답 파싱 없이 구현할 수 있다.
    // Jackson 2를 전이로 끌고 오지만 Jackson 3(tools.jackson)와 좌표가 달라 서로 무관하게 공존한다.
    implementation("com.google.firebase:firebase-admin:9.4.3")
    // 애플 revoke용 .p8(PEM) 개인키 파싱. 손으로 헤더를 떼고 Base64를 디코드하는 대신
    // 표준 PEM 파서를 쓴다. nimbus의 JWK.parseFromPEMEncodedObjects는 쓸 수 없다 —
    // .p8은 개인키만 담고 있는데 그 API는 JWK 조립에 공개키를 함께 요구한다.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("io.mockk:mockk:1.13.13")
    // 패키지 의존성 방향(infrastructure → application → domain) 검증
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    // 소스를 읽어야만 보이는 컨벤션(와일드카드 import)을 검사한다. ArchUnit은 바이트코드를 읽는데
    // import 형태는 거기 남지 않는다 — `import a.b.*`와 개별 import가 컴파일 후 구별되지 않는다.
    //
    // **Konsist는 자체 임베디드 컴파일러(2.0.20)로 파싱한다** — 이 프로젝트의 2.3.0이 아니다.
    // 그보다 나중 문법은 파싱에 실패할 수 있다. 2026-09-16 전체 소스(793파일)로 확인했으며,
    // Kotlin 버전을 올릴 때 이쪽이 먼저 깨질 수 있다는 뜻이다.
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
    // GraphQL ID 스칼라를 Long으로 생성한다. codegen 기본값은 String이라 리졸버마다 toString/toLong
    // 변환과 파싱 실패 분기가 생기는데, 이 스키마의 ID는 **전부 서버가 부여한 BIGINT PK**다
    // (spot.id, member.id, terms.id …). 문자열로 받아 곧바로 되돌리는 왕복이 순수한 잡음이었다.
    //
    // 매핑을 두면 생성 타입까지 Long이 되어 **경계에서 한 번만 검증된다** — 형식이 틀린 id는
    // 리졸버에 도달하기 전에 GraphQL이 자체 오류로 거부한다.
    typeMapping = mutableMapOf("ID" to "kotlin.Long")
}

// 생성된 GraphQL 타입을 **소스 디렉터리로 등록한다.**
//
// 등록하지 않아도 Gradle 빌드는 통과한다 — codegen 플러그인이 컴파일 태스크에 출력을 직접
// 얹기 때문이다. 문제는 IDE다: IntelliJ는 이 디렉터리가 소스인 줄 모르므로 생성 타입
// (CreatePostInput, PostTopic …)과 @InputArgument를 전부 미해결로 표시한다. 빌드는 되는데
// 에디터만 빨갛게 되는 상태이며, build/를 지우고 다시 임포트하면 잠깐 낫다가 재발한다.
//
// srcDir로 명시하면 Gradle 임포트가 이 경로를 IDE에 알려 준다.
sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/dgs-codegen"))
    }
}

// 컴파일 전에 코드 생성이 끝나 있도록 한다. 위 srcDir만 두면 IDE 임포트 시점에 디렉터리가
// 비어 있을 수 있고, 그러면 등록해 놓고도 미해결로 남는다.
//
// **IDE에서 빨간 줄이 보이면 `./gradlew generateJava`를 먼저 돌린 뒤 Gradle sync를 한다.**
// generateJava는 출력이 지워져도 UP-TO-DATE로 건너뛸 수 있어(빌드 캐시), 그 상태로 임포트하면
// 소스 루트는 등록돼 있는데 안이 비어 미해결로 보인다.
tasks.named("compileKotlin") {
    dependsOn(tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask>())
}

// 자동 생성 코드는 스타일 검사 대상이 아니다 — 위 srcDir 등록 때문에 main 소스셋에 들어온다.
tasks.withType<org.jmailen.gradle.kotlinter.tasks.LintTask>().configureEach {
    val generatedDir = layout.buildDirectory.dir("generated").get().asFile
    exclude { it.file.startsWith(generatedDir) }
}
tasks.withType<org.jmailen.gradle.kotlinter.tasks.FormatTask>().configureEach {
    val generatedDir = layout.buildDirectory.dir("generated").get().asFile
    exclude { it.file.startsWith(generatedDir) }
}

// plain jar는 라이브러리로 배포할 때 쓰는 산출물이라 이 프로젝트에는 쓸모가 없다. 켜 두면
// build/libs에 실행 불가한 jar(Main-Class 없음)가 함께 생겨 이미지에 잘못 실릴 수 있다.
tasks.jar { enabled = false }

// 파일명을 고정해 버전이 바뀌어도 Dockerfile을 손대지 않게 한다. 배포 식별은 이미지 태그가 맡는다.
tasks.bootJar { archiveFileName.set("app.jar") }

