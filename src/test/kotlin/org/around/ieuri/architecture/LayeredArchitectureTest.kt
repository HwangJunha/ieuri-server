package org.around.ieuri.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import kotlin.test.Test

/**
 * 헥사고날 의존성 방향 규칙을 검증한다: infrastructure → application → domain (안쪽으로만).
 * - domain: 아무것도 의존하지 않는다(application/infrastructure를 참조하면 위반).
 * - application: domain만 의존한다(infrastructure를 참조하면 위반).
 * - infrastructure: 바깥 계층이므로 다른 계층이 이를 의존할 수 없다.
 * 도메인 이름과 무관하게 ..domain.. / ..application.. / ..infrastructure.. 패키지 전체에 적용된다.
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
}
