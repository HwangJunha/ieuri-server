package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `@Enumerated(STRING)` 컬럼이 **가장 긴 열거값을 담을 수 있는지** 검사한다.
 *
 * 열거형에 값을 더할 때 컬럼 길이를 함께 보지 않으면 **INSERT에서만 터진다** — 애플리케이션도
 * 스키마도 CHECK 제약도 전부 통과하고, DB만 `value too long for type character varying(n)`으로
 * 거부한다. 유닛테스트는 저장소를 스텁하므로 잡지 못한다.
 *
 * 실제로 SPEC-0026이 `ReportTargetType`에 `SPOT_ANSWER_REPLY`(17자)를 더하면서
 * `VARCHAR(10)` 컬럼을 그대로 뒀고, 신고 요청이 런타임에 실패했다(V65에서 30으로 넓혔다).
 *
 * 이 테스트는 엔티티 메타데이터와 열거값만 보므로 DB도 Spring도 필요 없다.
 */
class EnumColumnLengthTest {

    private val entities: List<JavaClass> = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.around.ieuri")
        .filter { it.isAnnotatedWith(Entity::class.java) }

    @Test
    fun `문자열 열거 컬럼은 가장 긴 열거값을 담을 수 있다`() {
        val violations = entities.flatMap { entity ->
            entity.allFields.mapNotNull { field ->
                val enumerated = field.tryGetAnnotationOfType(Enumerated::class.java).orElse(null)
                    ?: return@mapNotNull null
                if (enumerated.value != EnumType.STRING) return@mapNotNull null

                val column = field.tryGetAnnotationOfType(Column::class.java).orElse(null)
                    ?: return@mapNotNull null
                // length 기본값 255는 명시하지 않은 것과 같아 넉넉하다.
                val declared = column.length

                val constants = Class.forName(field.rawType.name).enumConstants
                    ?: return@mapNotNull null
                val longest = constants.maxOf { (it as Enum<*>).name.length }

                if (longest <= declared) {
                    null
                } else {
                    "${entity.simpleName}.${field.name}: length=$declared 인데 " +
                        "가장 긴 값이 ${longest}자다"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "열거값이 컬럼 길이를 넘는다 — 마이그레이션으로 컬럼을 넓혀야 한다:\n" +
                violations.joinToString("\n"),
        )
    }
}
