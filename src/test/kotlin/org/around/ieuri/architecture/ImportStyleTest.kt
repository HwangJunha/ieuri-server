package org.around.ieuri.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * 와일드카드 import를 쓰지 않는다. 바이트코드에 import 형태가 남지 않아 Konsist로 소스를 읽는다.
 * 임시 고정이다 — 재검토 대상은 `problem/HARNESS-server-sensors.md`.
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
                      → 개별 import로 바꾼다.
                """.trimIndent(),
            ) { it.isWildcard }
    }
}
