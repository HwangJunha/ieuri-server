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
}
