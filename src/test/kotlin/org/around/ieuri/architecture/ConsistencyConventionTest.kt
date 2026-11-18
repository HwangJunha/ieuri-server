package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
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

    // ── context 간 통일(SPEC-0039 D-145·D-149·D-151·D-152) ─────────────────────

    /** 쓰기 유스케이스의 구현은 애그리게이트마다 `XxxModifyService` 하나다. */
    @Test
    fun `CommandUseCase를 구현하는 서비스는 ModifyService로 끝난다`() {
        classes().that().implement(JavaClass.Predicates.simpleNameEndingWith("CommandUseCase"))
            .should().haveSimpleNameEndingWith("ModifyService")
            .because("쓰기 서비스 이름은 XxxModifyService로 통일했다 — XxxCommandService나 유스케이스 묶음별 서비스로 나누지 말고 그 애그리게이트의 ModifyService에 넣는다")
            .check(classes)
    }

    /** 조회 쪽은 조회 포트로만 읽는다 — 저장소는 쓰기 쪽이 루트를 싣는 문이다. */
    @Test
    fun `QueryService는 저장소를 주입하지 않는다`() {
        noClasses().that().haveSimpleNameEndingWith("QueryService")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("조회 서비스는 XxxQueryPort로 읽는다. 판정할 규칙이 있으면 조회 포트가 값을 읽어 오고 도메인 함수가 판정한다")
            .check(classes)
    }

    /** 사용자 오류(`userErrors`)의 뿌리는 context마다 하나이고 `domain/shared`에 있다. */
    @Test
    fun `UserException 뿌리는 domain shared에 있다`() {
        classes().that().haveSimpleNameEndingWith("UserException")
            .should().resideInAPackage("..domain.shared..")
            .because("userErrors로 나가는 예외의 뿌리는 <Context>UserException 하나다 — 애그리게이트 패키지에 새 뿌리를 만들지 말고 그것을 상속한다")
            .check(classes)
    }

    /** Exposed 테이블은 애그리게이트마다 `<애그리게이트>Tables.kt` 한 파일에 모은다. */
    @Test
    fun `Exposed 테이블은 table 패키지의 Tables 파일에 선언한다`() {
        classes().that().areAssignableTo("org.jetbrains.exposed.v1.core.Table")
            .and().resideInAPackage("org.around.ieuri..")
            .should().resideInAPackage("..adapter.persistence.table..")
            .andShould(beDeclaredInFileEndingWith("Tables.kt"))
            .because("테이블 선언은 그 테이블을 쓰는 애그리게이트의 <애그리게이트>Tables.kt에 넣는다 — 테이블마다 파일을 만들지 않는다")
            .check(classes)
    }

    /** `adapter/<기술>/<역할>/` — 기술 폴더 이름은 context마다 같은 말을 쓴다. */
    @Test
    fun `adapter 바로 아래 폴더는 정해진 기술 이름만 쓴다`() {
        classes().that().resideInAPackage("org.around.ieuri.*.adapter..")
            .should().resideInAnyPackage(*ADAPTER_TECHNOLOGIES.map { "org.around.ieuri.*.adapter.$it.." }.toTypedArray())
            .because("adapter 아래는 기술 이름(${ADAPTER_TECHNOLOGIES.joinToString()})으로 나눈다 — 새 폴더를 만들기 전에 이 중 어디에 속하는지 본다")
            .check(classes)
    }

    private fun beDeclaredInFileEndingWith(suffix: String) = object : ArchCondition<JavaClass>("be declared in a file ending with $suffix") {
        override fun check(item: JavaClass, events: ConditionEvents) {
            val fileName = item.source.flatMap { it.fileName }.orElse("")
            if (!fileName.endsWith(suffix)) {
                events.add(SimpleConditionEvent.violated(item, "${item.name} is declared in $fileName"))
            }
        }
    }

    private companion object {
        val ADAPTER_TECHNOLOGIES = listOf("batch", "cache", "client", "config", "event", "graphql", "messaging", "persistence", "security", "storage")
    }
}
