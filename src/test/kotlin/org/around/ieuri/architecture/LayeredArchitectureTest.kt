package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import kotlin.test.Test

/**
 * 헥사고날 + 도메인 경계 규칙: 의존성 방향, domain 순수성, application의 DGS 격리,
 * 크로스도메인 내부 격리, 애그리게이트 간 호출, `copy` 제한, 빈 이름 충돌, AFTER_COMMIT 전파.
 */
class LayeredArchitectureTest {

    private val classes = ArchitectureClasses.main

    @Test
    fun `의존성은 adapter → application → domain 방향만 허용한다`() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapter").definedBy("..adapter..")
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
            .check(classes)
    }

    @Test
    fun `domain은 순수 Kotlin이어야 한다 - Spring DGS Exposed JDBC 의존 금지`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "com.netflix.graphql..",
                "com.netflix.dgs..",
                "graphql..",
                "org.jetbrains.exposed..",
                "java.sql..",
                "javax.sql..",
            )
            .because("domain/은 프레임워크에 의존하지 않는 순수 Kotlin이어야 한다")
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
            .because("read DTO → 응답 타입 매핑은 adapter/graphql/mapper가 한다")
            .check(classes)
    }

    /** 경계 타입은 adapter에서 멈춘다(POL-033). 생성 타입은 `common.graphql.generated`라 위 규칙에 걸리지 않는다. */
    @Test
    fun `application과 domain은 GraphQL 생성 타입과 Exposed 테이블 선언에 의존하지 않는다`() {
        noClasses().that().resideInAnyPackage("..application..", "..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.around.ieuri.common.graphql.generated..",
                "..adapter.persistence.table..",
            )
            .because("경계 타입 ↔ 도메인 변환은 각 adapter의 mapper에서 한다 — application·domain은 원시값·도메인 모델·read DTO만 안다")
            .check(classes)
    }

    /**
     * context 간 상호작용은 상대의 `provided`(유스케이스와 그 DTO) 또는 발행된 도메인 이벤트로만 한다.
     * - `common..`: 공유 커널.
     * - `..application..provided..`: 호출하는 쪽은 `adapter/client`여야 한다.
     * - `..domain..event..`: 구독하는 쪽은 `adapter/event`여야 한다(POL-028).
     */
    @Test
    fun `context는 다른 context의 내부에 의존하지 않는다`() {
        slices().matching("org.around.ieuri.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue<JavaClass>(),
                JavaClass.Predicates.resideInAPackage("org.around.ieuri.common.."),
            )
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..adapter.client.."),
                JavaClass.Predicates.resideInAPackage("..application..provided.."),
            )
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..adapter.event.."),
                JavaClass.Predicates.resideInAPackage("..domain..event.."),
            )
            .because("다른 context의 값은 adapter/client가 그쪽 provided 유스케이스로 읽고, 변화는 adapter/event가 그쪽 도메인 이벤트로 받는다")
            .check(classes)
    }

    /** 저장소는 루트마다 하나이고 그 애그리게이트의 서비스만 쓴다. */
    @Test
    fun `저장소는 자기 애그리게이트의 서비스만 쓴다`() {
        val offenders = classes
            .filter { APPLICATION_AGGREGATE.matches(it.packageName) }
            .flatMap { origin ->
                val (context, aggregate) = requireNotNull(APPLICATION_AGGREGATE.matchEntire(origin.packageName)).destructured
                origin.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { it.simpleName.endsWith("Repository") && it.packageName == "org.around.ieuri.$context.application.${aggregateOf(it)}.required" }
                    .filter { aggregateOf(it) != aggregate }
                    .map { "${origin.simpleName} → ${it.simpleName}" }
            }
            .toSet()

        check(offenders.isEmpty()) {
            "다른 애그리게이트의 저장소를 직접 쓴다 — 읽기는 그 애그리게이트의 QueryUseCase로, 변경은 그쪽 CommandUseCase로 한다:\n" +
                offenders.joinToString("\n") { "  $it" }
        }
    }

    /** 인바운드 어댑터는 유스케이스(`provided`)만 부른다. outbound 포트를 직접 잡으면 application을 건너뛴다. */
    @Test
    fun `인바운드 어댑터는 required 포트에 의존하지 않는다`() {
        noClasses().that().resideInAnyPackage(
            "..adapter.graphql..",
            "..adapter.event.listener..",
            "..adapter.batch..",
            "..adapter.security.authentication..",
            "..adapter.security.authorization..",
        )
            .should().dependOnClassesThat().resideInAPackage("..application..required..")
            .because("리졸버·리스너·배치·인증 필터는 provided의 유스케이스를 부른다 — 포트가 필요하면 그 포트를 쓰는 유스케이스를 application에 둔다")
            .check(classes)
    }

    private fun aggregateOf(type: JavaClass): String = APPLICATION_AGGREGATE.matchEntire(type.packageName)?.groupValues?.get(2).orEmpty()

    /** 애그리게이트끼리 서로를 알면 경계가 없는 것이다. 둘이 함께 쓰는 값은 `domain/shared`로 내린다. */
    @Test
    fun `도메인의 애그리게이트 패키지는 서로 순환하지 않는다`() {
        slices().matching("org.around.ieuri.(*).domain.(*)..")
            .should().beFreeOfCycles()
            .because("순환하는 두 애그리게이트는 하나이거나, 공유하는 값을 domain/shared로 내려야 한다")
            .check(classes)
    }

    /** 불변식은 도메인 메서드를 거쳐야만 바뀐다 — `copy`로 필드를 바꾸면 검증을 건너뛴다. */
    @Test
    fun `도메인 모델의 copy는 domain 안에서만 부른다`() {
        noClasses().that().resideOutsideOfPackage("..domain..")
            .should().callMethodWhere(
                DescribedPredicate.describe("도메인 모델의 copy") { call ->
                    call.targetOwner.packageName.contains(".domain.") && call.name.startsWith("copy")
                },
            )
            .because("상태 변경은 도메인 메서드로 한다 — application·adapter에서 copy로 필드를 바꾸지 말고 그 변경을 뜻하는 메서드를 루트에 둔다")
            .check(classes)
    }

    /**
     * 스테레오타입 빈의 단순 클래스명이 도메인 간에 겹치지 않는다 — 겹치면 기동에서만
     * ConflictingBeanDefinitionException이 난다. 빈 이름 명시 대신 클래스명에 도메인을 붙인다.
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
     * `AFTER_COMMIT` 리스너가 부르는 `@Transactional` 유스케이스는 `REQUIRES_NEW`여야 한다 —
     * REQUIRED면 커밋된 트랜잭션에 합류해 쓰기가 예외 없이 사라진다.
     * - private 헬퍼·중간 서비스를 거치는 호출도 잡도록 `org.around.ieuri` 안의 호출을 닫힐 때까지 따라간다.
     */
    @Test
    fun `AFTER_COMMIT 리스너가 부르는 쓰기 유스케이스는 REQUIRES_NEW여야 한다`() {
        val listenerMethods = classes
            .flatMap { it.methods }
            .filter { it.isAnnotatedWith("org.springframework.transaction.event.TransactionalEventListener") }

        // 호출 대상 타입 → 그 타입 자신과 그 타입을 구현한 클래스들(인터페이스 호출을 구현으로 되짚는다).
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

    private companion object {
        /** `org.around.ieuri.<context>.application.<aggregate>` 아래. */
        val APPLICATION_AGGREGATE = Regex("""org\.around\.ieuri\.(\w+)\.application\.(\w+)(?:\..*)?""")
    }
}
