package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

/**
 * 아키텍처 테스트가 공유하는 바이트코드 임포트. 테스트 JVM당 한 번만 읽는다(SPEC-0039 D-24).
 */
object ArchitectureClasses {
    val main: JavaClasses by lazy {
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.around.ieuri")
    }

    val test: JavaClasses by lazy {
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("org.around.ieuri")
    }
}
