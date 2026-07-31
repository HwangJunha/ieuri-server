package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.domain.aggregate.Spot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SpotCursorCodec — (거리, id) 복합키 라운드트립과 불투명성(AC-1). */
class SpotCursorCodecTest {

    @Test
    fun `인코딩-디코딩 라운드트립`() {
        val key = Spot.SortKey(1234.56, "126508")
        val decoded = SpotCursorCodec.decode(SpotCursorCodec.encode(key))
        assertEquals(key, decoded)
    }

    @Test
    fun `커서는 원문 id가 그대로 보이지 않는 불투명 토큰`() {
        val cursor = SpotCursorCodec.encode(Spot.SortKey(10.0, "999888"))
        assert(!cursor.contains("999888")) { "cursor leaked raw id: $cursor" }
    }

    @Test
    fun `잘못된 커서는 IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { SpotCursorCodec.decode("!!!not-base64!!!") }
        assertFailsWith<IllegalArgumentException> { SpotCursorCodec.decode("") }
    }
}
