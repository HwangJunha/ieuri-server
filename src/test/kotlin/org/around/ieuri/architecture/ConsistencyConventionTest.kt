package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import kotlin.test.Test

/**
 * 전수 조사에서 위반 0건이던 일관성을 고정한다 — import·트랜잭션 위치·네이밍.
 * 확정된 컨벤션이 아니라 임시다. 재검토 대상은 `problem/HARNESS-server-sensors.md`.
 */
class ConsistencyConventionTest {

    private val classes = ArchitectureClasses.main

    private val testClasses = ArchitectureClasses.test

    // ── 의존·import ────────────────────────────────────────────────────────────

    /** `application`은 영속 기술을 모른다 — 읽기 포트가 QueryDSL 타입을 돌려주면 read DTO 경계가 사라진다. */
    @Test
    fun `application은 JPA Hibernate QueryDSL에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "javax.persistence..",
                "org.hibernate..",
                "com.querydsl..",
            )
            .because("영속 기술은 infrastructure의 세부사항이다. application은 포트와 read DTO만 안다")
            .check(classes)
    }

    /** `domain`은 공유 커널조차 모른다. */
    @Test
    fun `domain은 common에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.around.ieuri.common..")
            .because("common은 얇게 유지한다. 도메인이 기대기 시작하면 추출이 막힌다")
            .check(classes)
    }

    // ── 트랜잭션 위치 ──────────────────────────────────────────────────────────

    /** 트랜잭션 경계는 유스케이스가 정한다. domain·리졸버에는 붙이지 않는다. */
    @Test
    fun `domain과 리졸버에는 Transactional을 붙이지 않는다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("domain은 프레임워크를 모른다")
            .check(classes)

        noClasses().that().resideInAPackage("..adapter.graphql..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("트랜잭션 경계는 유스케이스가 정한다. 리졸버에 붙이면 요청 전체로 넓어진다")
            .check(classes)

        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..adapter.graphql..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("트랜잭션 경계는 유스케이스가 정한다. 리졸버에 붙이면 요청 전체로 넓어진다")
            .check(classes)
    }

    // ── 네이밍 ────────────────────────────────────────────────────────────────

    /** Spring Data 인터페이스는 `Repository`로 끝나고 `persistence/repository/` 안에 있다. */
    @Test
    fun `Spring Data 리포지토리는 Repository로 끝나고 repository 패키지에 있다`() {
        classes().that().areAssignableTo("org.springframework.data.repository.Repository")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Repository")
            .andShould().resideInAPackage("..persistence.repository..")
            .because("리포지토리는 한 곳에 모인다 — 어디를 봐야 하는지가 이름과 위치로 정해진다")
            // Spring Data 리포지토리가 더 없다. JPA를 걷어내는 S16에서 규칙째 지운다.
            .allowEmptyShould(true)
            .check(classes)
    }

    /**
     * 아웃바운드 포트 인터페이스는 `Port`로 끝난다(SPEC-0039 D-17).
     * - infrastructure가 구현하는 인터페이스만 대상이다(결과 타입 `sealed interface`는 제외).
     */
    @Test
    fun `아웃바운드 포트 인터페이스는 Port로 끝난다`() {
        classes().that().resideInAPackage("..application.port.out..")
            .and().areInterfaces()
            .and(
                DescribedPredicate.describe("infrastructure가 구현하는") { port ->
                    port.allSubclasses.any { it.packageName.contains(".infrastructure.") }
                },
            )
            .should().haveSimpleNameEndingWith("Port")
            .because("포트는 이름으로 식별된다 — port/out의 인터페이스 이름을 XxxPort로 바꾼다")
            // 옛 구조(port/out)가 더 없다. S16에서 새 구조 규칙으로 바꾼다.
            .allowEmptyShould(true)
            .check(classes)
    }

    /** `Dto`는 쓰지 않는다. 읽기 모델의 단어는 `View`다. */
    @Test
    fun `Dto 접미사를 쓰지 않는다`() {
        noClasses().should().haveSimpleNameEndingWith("Dto")
            .because("읽기 모델은 View다. DTO는 모든 층의 타입을 삼키는 말이라 구별에 쓸 수 없다")
            .check(classes)
    }

    /** 테스트 클래스는 `Test`로 끝난다(중첩 클래스 제외). */
    @Test
    fun `테스트 클래스는 Test로 끝난다`() {
        classes().that().containAnyMethodsThat(
            DescribedPredicate.describe("@Test가 붙은") { method ->
                method.isAnnotatedWith("org.junit.jupiter.api.Test") ||
                    method.isAnnotatedWith("org.junit.jupiter.params.ParameterizedTest")
            },
        )
            .and().areNotNestedClasses()
            .should().haveSimpleNameEndingWith("Test")
            .because("테스트는 이름으로 식별된다 — Tests·Spec·IT이 섞이면 실행 대상 지정이 흔들린다")
            .check(testClasses)
    }
}
