package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

/**
 * 아키텍처 테스트가 공유하는 바이트코드 임포트. 테스트 JVM당 한 번만 읽는다.
 *
 * 테스트 클래스 필드로 두면 JUnit이 테스트 메서드마다 인스턴스를 새로 만들어 전체 클래스를
 * 매번 다시 읽는다 — 스위트 시간의 대부분이 거기 쓰였다(SPEC-0039 D-24).
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
