package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import kotlin.test.Test

/**
 * 헥사고날 + 도메인 경계 규칙: 의존성 방향, domain 순수성, @Entity 위치, application의 DGS 격리,
 * 크로스도메인 내부 격리, 빈 이름 충돌, AFTER_COMMIT 전파.
 */
class LayeredArchitectureTest {

    private val classes = ArchitectureClasses.main

    @Test
    fun `의존성은 infrastructure → application → domain 방향만 허용한다`() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            // `..adapter..`: Exposed로 옮긴 context의 새 구조(SPEC-0039 D-47). 옛 구조가 사라지면 `..infrastructure..`를 뺀다.
            .layer("Infrastructure").definedBy("..infrastructure..", "..adapter..")
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

    /** 경계 타입은 infrastructure에서 멈춘다(POL-033). 생성 타입은 `common.graphql.generated`라 위 규칙에 걸리지 않는다. */
    @Test
    fun `application과 domain은 GraphQL 생성 타입과 JPA 엔티티에 의존하지 않는다`() {
        noClasses().that().resideInAnyPackage("..application..", "..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.around.ieuri.common.graphql.generated..",
                "..persistence.entity..",
            )
            .orShould().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .because("경계 타입 ↔ 도메인 변환은 infrastructure 어댑터의 mapper에서 한다 — application·domain은 원시값·도메인 모델·read DTO만 안다")
            .check(classes)
    }

    /**
     * 도메인 간 상호작용은 상대 도메인의 `port/in` 또는 발행된 도메인 이벤트로만 한다.
     * - `common..`: 공유 커널.
     * - `..port.in..`·`..application.query..`: 호출하는 쪽은 `adapter.out.client`여야 한다.
     * - `..domain.event..`: 구독하는 쪽은 `adapter.in.event`여야 한다(POL-028).
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
            // 새 구조(SPEC-0039 D-47): `adapter.client` → 상대의 `provided`(포트·DTO), `adapter.event` → 상대의 `domain.event`.
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..adapter.client.."),
                JavaClass.Predicates.resideInAnyPackage("..application..provided..", "..application.port.in..", "..application.query.."),
            )
            .ignoreDependency(
                JavaClass.Predicates.resideInAPackage("..adapter.event.."),
                JavaClass.Predicates.resideInAPackage("..domain.event.."),
            )
            .because("도메인 간 상호작용은 상대 도메인의 port/in 또는 발행된 도메인 이벤트로만 한다")
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
}
