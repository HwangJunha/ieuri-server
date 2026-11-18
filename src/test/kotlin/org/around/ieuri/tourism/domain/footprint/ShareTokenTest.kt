package org.around.ieuri.tourism.domain.footprint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 공유 토큰(SPEC-0030). */
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

    @Test
    fun `공유 URL에서 토큰을 꺼낸다`() {
        assertEquals("kJ3xQ9", ShareToken.extractFrom("https://www.ieuri.co.kr/tour/korea-map/kJ3xQ9"))
    }

    @Test
    fun `도메인이나 경로가 바뀌어도 같은 토큰이 나온다`() {
        // 경로 마지막 조각만 보므로 링크 형태가 바뀌어도 서버가 안 바뀐다.
        assertEquals("kJ3xQ9", ShareToken.extractFrom("https://ieuri.co.kr/shared/kJ3xQ9"))
    }

    @Test
    fun `쿼리 문자열이 붙어도 토큰만 꺼낸다`() {
        // 공유 시트나 메신저가 추적 파라미터를 붙이는 경우가 있다.
        assertEquals("kJ3xQ9", ShareToken.extractFrom("https://www.ieuri.co.kr/tour/korea-map/kJ3xQ9?utm_source=kakao"))
    }
}
