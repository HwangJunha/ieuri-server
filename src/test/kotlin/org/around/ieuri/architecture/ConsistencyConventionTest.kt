package org.around.ieuri.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import kotlin.test.Test

/**
 * **지금 이미 지켜지고 있는 일관성**을 고정한다. `LayeredArchitectureTest`가 의존성 *방향*을
 * 본다면 이쪽은 그 밖의 컨벤션 — import·트랜잭션 위치·네이밍 — 을 본다.
 *
 * ## 이 테스트의 성격: 임시다
 *
 * 여기 있는 규칙은 "이것이 옳은 컨벤션이라서"가 아니라 **"전수 조사 결과 위반이 0건이라서"**
 * 골랐다(2026-09-16, main 623파일 / test 169파일). 둘은 다르다.
 *
 * **리팩토링에서 재검토된다.** 컨벤션을 제대로 정하는 일은 아직 안 했고, 실제로 현재 일관성과
 * 올바른 이름이 어긋나는 자리가 이미 보인다 — 엔티티 변환 동사가 `toEntity`가 아니라
 * `toNewEntity`(21건, plain `toEntity`는 0건)인 것이 그 예다.
 *
 * 그러니 **이 파일을 "정해진 컨벤션"의 근거로 읽지 말 것.** 센서가 근거 없는 권위가 되는 것이
 * 이 주석이 막으려는 일이다. 재검토 대상 목록은 `problem/HARNESS-server-sensors.md`에 있다.
 *
 * 다만 여기 있는 대부분(`domain`의 `common` 격리, application의 영속 기술 격리)은 컨벤션
 * 재정의와 무관하게 계속 유효하다. 흔들릴 수 있는 것은 네이밍 계열이며, 그래서 네이밍은
 * **리팩토링에서 바뀔 이유가 없는 셋**만 넣었다(`Repository`·`Dto` 금지·`Test`).
 * `UseCase`·`Adapter`·`View`처럼 지금 여러 갈래인 것은 넣지 않았다 — 2차에서 정한다.
 *
 * 와일드카드 import 금지(현재 792파일 중 0건)는 여기 없다. 바이트코드에 import 형태가 남지
 * 않아 ArchUnit으로는 볼 수 없다 — 소스를 읽는 도구가 필요하다.
 */
class ConsistencyConventionTest {

    private val classes = ArchitectureClasses.main

    private val testClasses = ArchitectureClasses.test

    // ── 의존·import ────────────────────────────────────────────────────────────

    /**
     * `application`은 영속 기술을 모른다. `LayeredArchitectureTest`가 DGS만 막고 있어
     * JPA·QueryDSL 쪽은 열려 있었다 — 읽기 포트가 QueryDSL 타입을 그대로 돌려주기 시작하면
     * CQRS의 read DTO 경계가 조용히 사라진다.
     */
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

    /**
     * `domain`은 공유 커널조차 모른다. `common/`이 비대해지면 도메인 추출이 막히는데, 그 비대함은
     * 대개 "도메인이 쓰니까"로 정당화되며 자란다. 현재 94개 도메인 파일 중 `common`을 쓰는 것이
     * 하나도 없으므로 그 상태를 고정한다.
     */
    @Test
    fun `domain은 common에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.around.ieuri.common..")
            .because("common은 얇게 유지한다. 도메인이 기대기 시작하면 추출이 막힌다")
            .check(classes)
    }

    // ── 트랜잭션 위치 ──────────────────────────────────────────────────────────

    /**
     * 트랜잭션 경계는 application(유스케이스)과 그것을 감싸는 어댑터의 몫이다.
     *
     * `domain`에 붙으면 순수성이 깨지고, **리졸버에 붙으면 경계가 요청 단위로 넓어진다** —
     * 필드 리졸버마다 I/O가 따로 일어나는 이 구조에서는 트랜잭션이 필요 이상으로 길어진다.
     */
    @Test
    fun `domain과 리졸버에는 Transactional을 붙이지 않는다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("domain은 프레임워크를 모른다")
            .check(classes)

        noClasses().that().resideInAPackage("..adapter.in.graphql..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("트랜잭션 경계는 유스케이스가 정한다. 리졸버에 붙이면 요청 전체로 넓어진다")
            .check(classes)

        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..adapter.in.graphql..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("트랜잭션 경계는 유스케이스가 정한다. 리졸버에 붙이면 요청 전체로 넓어진다")
            .check(classes)
    }

    // ── 네이밍 ────────────────────────────────────────────────────────────────
    // 리팩토링에서 바뀔 이유가 없는 셋만 둔다. 클래스 위 KDoc 참조.

    /**
     * Spring Data 인터페이스는 `Repository`로 끝나고 `persistence/repository/` 안에 있다.
     * 현재 46개가 전부 그렇다.
     */
    @Test
    fun `Spring Data 리포지토리는 Repository로 끝나고 repository 패키지에 있다`() {
        classes().that().areAssignableTo("org.springframework.data.repository.Repository")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Repository")
            .andShould().resideInAPackage("..persistence.repository..")
            .because("리포지토리는 한 곳에 모인다 — 어디를 봐야 하는지가 이름과 위치로 정해진다")
            .check(classes)
    }

    /**
     * 아웃바운드 포트 인터페이스는 `Port`로 끝난다(SPEC-0039 D-17). 연산이 없는 인터페이스는 포트가 아니라
     * 포트가 돌려주는 결과 타입(`sealed interface SnsRevokeResult`)이라 대상에서 뺀다.
     */
    @Test
    fun `아웃바운드 포트 인터페이스는 Port로 끝난다`() {
        classes().that().resideInAPackage("..application.port.out..")
            .and().areInterfaces()
            .and(DescribedPredicate.describe("연산을 선언한") { it.methods.isNotEmpty() })
            .should().haveSimpleNameEndingWith("Port")
            .because("포트는 이름으로 식별된다 — port/out의 인터페이스 이름을 XxxPort로 바꾼다")
            .check(classes)
    }

    /**
     * `Dto`는 쓰지 않는다. 읽기 모델의 단어는 `View`다(현재 50개).
     *
     * "DTO"는 커맨드 입력·도메인 모델·JPA 엔티티·읽기 모델·GraphQL 응답을 전부 삼키는 말이라,
     * 한 번 들어오면 어느 층의 무엇인지 이름으로 구별되지 않는다. 현재 0건이므로 0을 지킨다.
     */
    @Test
    fun `Dto 접미사를 쓰지 않는다`() {
        noClasses().should().haveSimpleNameEndingWith("Dto")
            .because("읽기 모델은 View다. DTO는 모든 층의 타입을 삼키는 말이라 구별에 쓸 수 없다")
            .check(classes)
    }

    /** 테스트 클래스는 `Test`로 끝난다. 현재 167/169이고 나머지 둘은 테스트가 아닌 Stub이다. */
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
