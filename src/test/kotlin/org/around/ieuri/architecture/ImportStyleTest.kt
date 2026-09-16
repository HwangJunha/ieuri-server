package org.around.ieuri.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * import 형태를 고정한다. **ArchUnit으로는 불가능한 검사**라 여기만 Konsist를 쓴다 —
 * 바이트코드에는 import가 남지 않아 `import a.b.*`와 개별 import가 컴파일 후 구별되지 않는다.
 *
 * 와일드카드 import는 현재 main·test 합쳐 792파일 중 **0건**이다(2026-09-16). 그 상태를 지킨다.
 *
 * 막으려는 것은 두 가지다. 어떤 이름이 어디서 왔는지가 파일 안에서 사라지고(도메인 간 같은
 * 이름의 타입이 여럿인 이 구조에서 특히 문제가 된다), 상대 패키지에 타입이 추가되는 것만으로
 * 이쪽 파일의 이름 해석이 조용히 바뀐다.
 *
 * 이 규칙은 [ConsistencyConventionTest]와 성격이 같다 — 지금 일관된 것을 고정할 뿐이며
 * 리팩토링에서 재검토된다. 다만 이 규칙 자체는 컨벤션 재정의와 무관하게 유효할 것이다.
 */
class ImportStyleTest {

    @Test
    fun `와일드카드 import를 쓰지 않는다`() {
        Konsist.scopeFromProject()
            .imports
            .assertFalse(
                additionalMessage = """
                    [konsist] 와일드카드 import가 있습니다.
                      → 이름의 출처가 파일 안에서 사라지고, 상대 패키지에 타입이 추가되는 것만으로
                        이 파일의 이름 해석이 바뀝니다. 개별 import로 바꾸세요.
                      → 이 결정과 충돌한다면 problem/SPEC-XXXX-server.md의 결정 절을 다시 보세요.
                """.trimIndent(),
            ) { it.isWildcard }
    }
}
