package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import kotlin.test.Test

/**
 * 헥사고날 + 도메인 경계 규칙을 ArchUnit으로 검증한다(테스트 시점 게이트).
 * 과거 PreToolUse 훅(block-architecture-violations.sh)이 쓰기 시점에 막던 규칙들을
 * 이 테스트로 이관해 단일 메커니즘(ArchUnit)으로 통합했다:
 *   1) 의존성 방향  2) domain 순수성  3) @Entity 위치  4) application의 DGS 격리
 *   5) 크로스도메인 내부 격리
 */
class LayeredArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.around.ieuri")

    @Test
    fun `의존성은 infrastructure → application → domain 방향만 허용한다`() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .check(classes)
    }

    @Test
    fun `domain은 순수 Kotlin이어야 한다 - JPA DGS Spring QueryDSL 의존 금지`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "javax.persistence..",
                "org.springframework..",
                "com.netflix.graphql..",
                "com.netflix.dgs..",
                "graphql..",
                "com.querydsl..",
            )
            .because("domain/은 프레임워크에 의존하지 않는 순수 Kotlin이어야 한다")
            .check(classes)
    }

    @Test
    fun `JPA 엔티티는 persistence 어댑터 안에만 존재한다`() {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infrastructure.adapter.out.persistence..")
            .because("@Entity는 영속 어댑터 세부사항이며 도메인 모델이 될 수 없다")
            .check(classes)
    }

    @Test
    fun `application은 DGS GraphQL 타입에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.netflix.graphql..",
                "com.netflix.dgs..",
                "graphql..",
            )
            .because("read DTO → 응답 타입 매핑은 graphql 리졸버(adapter/in)의 책임이다")
            .check(classes)
    }

    /**
     * 도메인 간 상호작용은 **상대 도메인의 `port/in`으로만** 한다. 규칙의 핵심은 "의존이 없다"가
     * 아니라 "의존이 인바운드 포트를 통한다"이므로, 두 예외를 명시적으로 둔다.
     *
     *  - `common..`      : 공유 커널. 모든 도메인이 쓴다.
     *  - `..port.in..`   : 상대 도메인의 인바운드 포트와 그것이 노출하는 read DTO(`application.query`).
     *                      호출하는 쪽은 반드시 `infrastructure`여야 한다 — 도메인·애플리케이션이
     *                      다른 도메인의 이름을 알면 추출이 불가능해진다.
     *  - `..domain.event..` : 발행된 도메인 이벤트. 구독하는 쪽은 반드시 인바운드 이벤트
     *                      어댑터(`infrastructure.adapter.in.event`)여야 한다. 이벤트 타입은
     *                      발행/구독의 **계약**이라 내부가 아니다 — Kafka로 옮기면 같은 자리에
     *                      메시지 스키마가 온다(POL-028). 애플리케이션 레이어는 이 타입을
     *                      알지 못하며, 어댑터가 자기 도메인의 port/in으로 번역한다.
     */
    @Test
    fun `도메인은 다른 도메인의 내부에 의존하지 않는다`() {
        slices().matching("org.around.ieuri.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue<JavaClass>(),
                JavaClass.Predicates.resideInAPackage("org.around.ieuri.common.."),
            )
            // 인바운드 포트 호출은 어댑터(infrastructure)에서만 허용한다.
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..infrastructure.adapter.out.client.."),
                JavaClass.Predicates.resideInAPackage("..application.port.in.."),
            )
            // 인바운드 포트가 돌려주는 read DTO. 포트만 허용하고 반환 타입을 막으면 포트를 쓸 수 없다.
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..infrastructure.adapter.out.client.."),
                JavaClass.Predicates.resideInAPackage("..application.query.."),
            )
            // 발행된 도메인 이벤트 구독은 인바운드 이벤트 어댑터에서만 허용한다.
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..infrastructure.adapter.in.event.."),
                JavaClass.Predicates.resideInAPackage("..domain.event.."),
            )
            .because("도메인 간 상호작용은 상대 도메인의 port/in 또는 발행된 도메인 이벤트로만 한다")
            .check(classes)
    }

    /**
     * 스테레오타입 빈의 **단순 클래스명이 겹치지 않아야 한다.**
     *
     * Spring은 이름을 명시하지 않은 `@Component` 계열의 빈 이름을 단순 클래스명으로 짓는다.
     * 그래서 패키지가 달라도 이름이 같으면 컴포넌트 스캔이 실패한다
     * (`ConflictingBeanDefinitionException`) — **컴파일과 단위 테스트는 전부 통과하고
     * 기동에서만 터진다.**
     *
     * 도메인마다 같은 역할의 어댑터를 두는 이 구조에서는 이름이 겹치기 쉽다(실제로
     * `MemberWithdrawnListener`가 community·tourism 양쪽에 생겨 기동이 막혔다).
     * 빈 이름을 명시하는 대신 클래스명에 도메인을 붙이는 쪽을 관례로 삼는다 —
     * 로그와 스택트레이스에서도 어느 도메인인지 바로 보인다.
     */
    @Test
    fun `스프링 빈의 단순 클래스명은 도메인 간에 겹치지 않는다`() {
        val stereotypes = setOf(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.context.annotation.Configuration",
            "com.netflix.graphql.dgs.DgsComponent",
        )

        val duplicates = classes
            .filter { candidate -> stereotypes.any { candidate.isAnnotatedWith(it) } }
            // 빈 이름을 직접 지정했다면 클래스명이 겹쳐도 충돌하지 않는다.
            .filterNot { it.isAnnotatedWith("org.springframework.context.annotation.Bean") }
            .groupBy { it.simpleName }
            .filterValues { it.size > 1 }

        check(duplicates.isEmpty()) {
            "빈 이름이 충돌하는 클래스가 있다(기동 시 ConflictingBeanDefinitionException):\n" +
                duplicates.entries.joinToString("\n") { (name, found) ->
                    "  $name → ${found.joinToString(", ") { it.name }}"
                }
        }
    }

    /**
     * `AFTER_COMMIT` 리스너가 부르는 유스케이스는 **`REQUIRES_NEW`여야 한다.**
     *
     * 그 시점의 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 묶여 있다. 기본 전파(REQUIRED)로
     * 두면 커밋할 수 없는 그 트랜잭션에 조용히 합류해 **쓰기가 흘러가지 않는다** — 예외도
     * 나지 않아 성공한 것처럼 보인다. 실제로 탈퇴 후 글·댓글이 남는 사고가 났다.
     *
     * 목으로 짠 단위 테스트는 전파를 볼 수 없고 컴파일도 통과하므로, 여기서 구조로 막는다.
     * 읽기 전용이거나 DB를 만지지 않는 리스너(메일 발송 등)는 대상이 아니다.
     *
     * **호출을 끝까지 따라간다.** 직접 호출만 보면 두 가지가 통째로 빠져나간다.
     *  - 리스너가 private 헬퍼를 거쳐 부르는 경우(`AlarmPushTriggerListener.dispatchQuietly`).
     *    예외를 삼키려면 헬퍼가 자연스러운 형태라 흔하다.
     *  - 트랜잭션 없는 서비스를 하나 거쳐 다른 빈이 쓰는 경우(`PushDispatchService` →
     *    `PushDispatchStore`). 원격 호출을 트랜잭션 밖으로 빼려면 빈을 나눠야 하므로
     *    그 구조 자체는 정당하고, 따라서 규칙이 따라가야 한다.
     *
     * 그래서 리스너에서 시작해 `org.around.ieuri` 안의 호출을 닫힐 때까지 훑는다.
     */
    @Test
    fun `AFTER_COMMIT 리스너가 부르는 쓰기 유스케이스는 REQUIRES_NEW여야 한다`() {
        val listenerMethods = classes
            .flatMap { it.methods }
            .filter { it.isAnnotatedWith("org.springframework.transaction.event.TransactionalEventListener") }

        // 호출 대상 타입 → 그 타입 자신과, 그 타입을 구현한 클래스들.
        // 인터페이스로 호출하므로 구현으로 되짚어야 하고, PushDispatchStore처럼 인터페이스가
        // 없는 빈은 자기 자신으로 잡힌다.
        fun implementationsOf(target: JavaClass): List<JavaClass> = classes.filter { it == target || it.allRawInterfaces.contains(target) }

        // 리스너에서 도달 가능한 우리 코드 전부. 방문 집합으로 순환을 끊는다.
        val reachable = mutableSetOf<JavaClass>()
        val queue = ArrayDeque(listenerMethods.map { it.owner }.distinct())
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!reachable.add(current)) continue
            current.methods
                .flatMap { it.methodCallsFromSelf }
                .map { it.targetOwner }
                .filter { it.packageName.startsWith("org.around.ieuri") }
                .flatMap { implementationsOf(it) }
                .forEach { if (it !in reachable) queue.addLast(it) }
        }

        val calledUseCases = reachable
            .filter { it.isAnnotatedWith("org.springframework.stereotype.Service") }

        val offenders = calledUseCases.flatMap { service ->
            service.methods
                .filter { it.isAnnotatedWith("org.springframework.transaction.annotation.Transactional") }
                .filterNot { method ->
                    val annotation = method.getAnnotationOfType(
                        "org.springframework.transaction.annotation.Transactional",
                    )
                    annotation.properties["propagation"]?.toString()?.contains("REQUIRES_NEW") == true
                }
                .map { "${service.simpleName}.${it.name}()" }
        }

        check(offenders.isEmpty()) {
            "AFTER_COMMIT 리스너가 부르는데 REQUIRES_NEW가 아니다(쓰기가 조용히 사라진다):\n" +
                offenders.joinToString("\n") { "  $it" }
        }
    }
}
