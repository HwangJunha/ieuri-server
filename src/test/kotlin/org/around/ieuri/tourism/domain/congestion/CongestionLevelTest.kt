package org.around.ieuri.tourism.domain.congestion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 업스트림 한국어 문자열 → 단계 enum 매핑(SPEC-0014 R7·R10). 정의되지 않은 값은 null이다. */
class CongestionLevelTest {

    @Test
    fun `혼잡도 4값이 각각 매핑된다`() {
        // AC R7.
        assertEquals(CongestionLevel.RELAXED, CongestionLevel.fromUpstreamOrNull("여유"))
        assertEquals(CongestionLevel.NORMAL, CongestionLevel.fromUpstreamOrNull("보통"))
        assertEquals(CongestionLevel.SLIGHTLY_CROWDED, CongestionLevel.fromUpstreamOrNull("약간 붐빔"))
        assertEquals(CongestionLevel.CROWDED, CongestionLevel.fromUpstreamOrNull("붐빔"))
    }

    @Test
    fun `앞뒤 공백은 무시한다`() {
        assertEquals(CongestionLevel.CROWDED, CongestionLevel.fromUpstreamOrNull("  붐빔 "))
    }

    @Test
    fun `정의되지 않은 문자열은 매핑 실패다`() {
        // AC R7.
        assertNull(CongestionLevel.fromUpstreamOrNull("매우 붐빔"))
        assertNull(CongestionLevel.fromUpstreamOrNull(""))
        assertNull(CongestionLevel.fromUpstreamOrNull(null))
    }

    @Test
    fun `혼잡도와 붐빔은 부분 문자열로 섞이지 않는다`() {
        // "약간 붐빔"과 "붐빔"이 서로를 잡아먹으면 단계가 한 칸씩 밀린다.
        assertEquals(CongestionLevel.SLIGHTLY_CROWDED, CongestionLevel.fromUpstreamOrNull("약간 붐빔"))
        assertEquals(CongestionLevel.CROWDED, CongestionLevel.fromUpstreamOrNull("붐빔"))
    }

    @Test
    fun `상권 4값이 어간으로 매핑된다`() {
        // 업스트림은 "한산한"을 준다 — 매뉴얼의 "한산한 시간대"와 다르다.
        assertEquals(CommercialActivityLevel.QUIET, CommercialActivityLevel.fromUpstreamOrNull("한산한"))
        assertEquals(CommercialActivityLevel.NORMAL, CommercialActivityLevel.fromUpstreamOrNull("보통"))
        assertEquals(CommercialActivityLevel.BUSY, CommercialActivityLevel.fromUpstreamOrNull("바쁜"))
        assertEquals(CommercialActivityLevel.PEAK, CommercialActivityLevel.fromUpstreamOrNull("분주한"))
    }

    @Test
    fun `상권은 시간대 접미사가 붙어도 같은 값이다`() {
        assertEquals(CommercialActivityLevel.QUIET, CommercialActivityLevel.fromUpstreamOrNull("한산한 시간대"))
        assertEquals(CommercialActivityLevel.PEAK, CommercialActivityLevel.fromUpstreamOrNull("분주한 시간대"))
    }

    @Test
    fun `상권은 영문 병기가 붙어도 같은 값이다`() {
        assertEquals(
            CommercialActivityLevel.QUIET,
            CommercialActivityLevel.fromUpstreamOrNull("한산한 시간대(Quiet Time)"),
        )
    }

    @Test
    fun `정의되지 않은 상권 문자열은 매핑 실패다`() {
        // AC R10. 호출자는 이 null을 상권 "부재"로 다룬다.
        assertNull(CommercialActivityLevel.fromUpstreamOrNull("혼잡한"))
        assertNull(CommercialActivityLevel.fromUpstreamOrNull(null))
    }
}
