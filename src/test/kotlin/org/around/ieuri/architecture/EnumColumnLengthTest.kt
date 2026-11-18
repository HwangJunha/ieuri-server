package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaClass
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `@Enumerated(STRING)` 컬럼이 가장 긴 열거값을 담을 수 있는지 검사한다.
 * - 넘으면 INSERT에서만 `value too long`으로 터진다 — 유닛테스트는 저장소를 스텁하므로 잡지 못한다.
 */
class EnumColumnLengthTest {

    private val entities: List<JavaClass> = ArchitectureClasses.main
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
