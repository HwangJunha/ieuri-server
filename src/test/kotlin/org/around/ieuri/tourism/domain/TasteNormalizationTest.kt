package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.strategy.TasteNormalization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 취향 요인 정규화(SPEC-0036 후속). 순수 함수라 스프링·DB가 없다. */
class TasteNormalizationTest {

    @Test
    fun `최소는 0 최대는 1이 되고 사이는 선형이다`() {
        // 압축된 구간(0.468~0.548).
        val result = TasteNormalization.normalize(
            mapOf(1L to 0.468, 2L to 0.508, 3L to 0.548),
        )

        assertEquals(0.0, result.getValue(1L), 1e-9)
        assertEquals(0.5, result.getValue(2L), 1e-9)
        assertEquals(1.0, result.getValue(3L), 1e-9)
    }

    @Test
    fun `순서는 보존된다`() {
        val input = mapOf(1L to 0.4779, 2L to 0.5508, 3L to 0.4980, 4L to 0.5386)

        val result = TasteNormalization.normalize(input)

        // 원값의 대소 관계가 그대로여야 한다 — 펼치기지 재배열이 아니다.
        val byInput = input.entries.sortedBy { it.value }.map { it.key }
        val byOutput = result.entries.sortedBy { it.value }.map { it.key }
        assertEquals(byInput, byOutput)
    }

    @Test
    fun `행을 버리지 않는다`() {
        val input = mapOf(1L to 0.47, 2L to 0.52, 3L to 0.50)

        val result = TasteNormalization.normalize(input)

        assertEquals(input.keys, result.keys)
    }

    @Test
    fun `값이 전부 같으면 중립값이다`() {
        // 폭이 0이면 가운데 값을 준다.
        val result = TasteNormalization.normalize(mapOf(1L to 0.5, 2L to 0.5, 3L to 0.5))

        assertTrue(result.values.all { it == 0.5 })
    }

    @Test
    fun `후보가 하나면 중립값이다`() {
        // 혼자서는 집단 안의 상대 위치가 정의되지 않는다.
        assertEquals(0.5, TasteNormalization.normalize(mapOf(7L to 0.9)).getValue(7L), 1e-9)
    }

    @Test
    fun `비어 있으면 비어 있다`() {
        assertTrue(TasteNormalization.normalize(emptyMap()).isEmpty())
    }

    @Test
    fun `폭이 좁아도 그대로 펼친다`() {
        // 사실상 균질한 집단도 가드 없이 0~1로 늘어난다.
        val result = TasteNormalization.normalize(mapOf(1L to 0.4951, 2L to 0.5014))

        assertEquals(0.0, result.getValue(1L), 1e-9)
        assertEquals(1.0, result.getValue(2L), 1e-9)
    }
}
