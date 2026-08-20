package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.ShareToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 공유 토큰(SPEC-0030 R12). 추측 가능하면 남의 지도가 열린다. */
class ShareTokenTest {

    @Test
    fun `토큰은 URL에 그대로 넣을 수 있는 문자만 쓴다`() {
        // 링크에 담기므로 +, /, = 가 있으면 인코딩 사고가 난다.
        repeat(50) {
            assertTrue(ShareToken.generate().matches(Regex("[A-Za-z0-9_-]+")))
        }
    }

    @Test
    fun `192비트를 base64url로 담아 32자다`() {
        assertEquals(32, ShareToken.generate().length)
    }

    @Test
    fun `연달아 만들어도 겹치지 않는다`() {
        val tokens = List(1_000) { ShareToken.generate() }

        assertEquals(1_000, tokens.toSet().size)
    }
}
