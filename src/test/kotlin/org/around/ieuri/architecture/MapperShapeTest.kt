package org.around.ieuri.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * 변환 함수의 **모양**을 고정한다(POL-033). 매퍼는 source 타입 위 확장 함수여야 한다:
 *
 * ```
 * fun SpotEntity.toDomain(): Spot          // 통과
 * private fun toDomain(e: SpotEntity)      // 위반 — 클래스 안 멤버
 * ```
 *
 * 멤버로 두면 그 클래스를 세우지 않고는 단위 테스트를 할 수 없고, 같은 타입 두 필드가 뒤바뀐
 * 매핑(`mapy → longitude`)을 컴파일러도 테스트도 잡지 못한다.
 *
 * **ArchUnit으로는 불가능하다.** 확장 함수와 멤버 함수는 컴파일하면 둘 다 static 메서드가 되어
 * 바이트코드에서 구별되지 않는다. 소스를 읽는 Konsist가 필요한 자리다.
 *
 * **이번 슬라이스가 더하거나 바꾼 파일만 검사한다**([ChangedFiles]) — 기존 위반 34건은 규칙보다
 * 오래된 코드이고, 전체를 검사하면 켜는 순간 빨개져 꺼진다.
 *
 * 슬라이스의 한 조각이 서면 돌린다(매 편집이 아니다). 요구사항을 따라 움직이는 테스트가 아니라
 * 구조 검사이므로 "테스트는 PR 직전 1회" 방침과 충돌하지 않는다.
 */
class MapperShapeTest {

    private companion object {
        /**
         * 경계를 넘는 변환 함수의 이름. 이 목록에 없으면 헬퍼로 보고 검사하지 않는다.
         *
         * 이름으로 좁히는 이유: 패키지 전체를 검사했더니 15건 중 14건이 `parseBasicIsoDate`,
         * `toIntOrZero` 같은 파싱 헬퍼였다. 헬퍼는 매퍼 파일에 같이 있을 뿐 경계를 넘지 않아
         * POL-033의 대상이 아니다. 넓히면 센서가 소음이 되어 무시된다.
         */
        val CONVERSION_NAMES = listOf(
            "toDomain", "toEntity", "toNewEntity", "toGql", "toCommand", "toView", "toPayload",
        )

        fun KoFunctionDeclaration.isConversion() = CONVERSION_NAMES.any { name.startsWith(it) }

        const val HINT = "→ 이 결정과 충돌한다면 problem/SPEC-XXXX-server.md의 결정 절을 다시 보라."
    }

    @Test
    fun `mapper 패키지의 변환 함수는 public top-level 확장 함수다`() {
        Konsist.scopeFromProject()
            .functions(includeNested = true, includeLocal = true)
            .filter { ChangedFiles.contains(it.containingFile.path) }
            // GraphQL 입력 매퍼가 adapter/in/graphql/mapper에도 있어 out만 보면 통째로 빠진다.
            .filter { it.resideInPackage("..adapter..mapper..") }
            .filter { it.isConversion() }
            .assertTrue(
                additionalMessage = """
                    [konsist] mapper 패키지의 변환 함수가 top-level 확장 함수가 아니다.
                      → POL-033: 매퍼는 source 타입 위에 선언한다 (fun SpotEntity.toDomain(): Spot).
                      $HINT
                """.trimIndent(),
            ) { it.isTopLevel && it.hasReceiverType() && it.hasPublicOrDefaultModifier }
    }

    @Test
    fun `변환 함수를 어댑터나 리졸버 안에 private 멤버로 숨기지 않는다`() {
        Konsist.scopeFromProject()
            .classes(includeNested = true)
            .filter { ChangedFiles.contains(it.containingFile.path) }
            .filter { it.resideInPackage("..infrastructure..") }
            .flatMap { it.functions() }
            .filter { it.isConversion() }
            .assertFalse(
                additionalMessage = """
                    [konsist] 변환 함수가 클래스 안 private 멤버로 선언돼 있다.
                      → POL-033: mapper 패키지의 top-level 확장 함수로 분리한다 — 멤버로 두면
                        그 클래스를 세우지 않고는 단위 테스트를 할 수 없고, 같은 타입 두 필드가
                        뒤바뀐 매핑을 컴파일러도 테스트도 잡지 못한다.
                      $HINT
                """.trimIndent(),
            ) { it.hasPrivateModifier }
    }
}
