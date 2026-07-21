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

    @Test
    fun `도메인은 다른 도메인의 내부에 의존하지 않는다`() {
        slices().matching("org.around.ieuri.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue<JavaClass>(),
                JavaClass.Predicates.resideInAPackage("org.around.ieuri.common.."),
            )
            .because("도메인 간 상호작용은 상대 도메인의 port/in 또는 발행된 도메인 이벤트로만 한다")
            .check(classes)
    }
}
