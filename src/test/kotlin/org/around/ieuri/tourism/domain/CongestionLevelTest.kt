package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.CommercialActivityLevel
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 업스트림 한국어 문자열 → 단계 enum 매핑(SPEC-0014 R7·R10).
 *
 * **정의되지 않은 값을 임의 단계로 넘기지 않는다**는 것이 이 테스트가 지키는 계약이다 —
 * 업스트림이 5번째 값을 도입했을 때 조용히 잘못된 색을 칠하는 것보다 표시하지 않는 편이 낫다.
 */
class CongestionLevelTest {

    @Test
    fun `혼잡도 4값이 각각 매핑된다`() {
        // AC R7. 업스트림 문자열과 enum의 대응이 계약이다.
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
        // AC R7. 임의 단계로 넘기지 않는다 — 이 null이 "그 구역을 건너뛴다"로 이어진다.
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
        // 실측(2026-08-10)에서 업스트림은 "한산한"을 준다 — 매뉴얼의 "한산한 시간대"와 다르다.
        assertEquals(CommercialActivityLevel.QUIET, CommercialActivityLevel.fromUpstreamOrNull("한산한"))
        assertEquals(CommercialActivityLevel.NORMAL, CommercialActivityLevel.fromUpstreamOrNull("보통"))
        assertEquals(CommercialActivityLevel.BUSY, CommercialActivityLevel.fromUpstreamOrNull("바쁜"))
        assertEquals(CommercialActivityLevel.PEAK, CommercialActivityLevel.fromUpstreamOrNull("분주한"))
    }

    @Test
    fun `상권은 시간대 접미사가 붙어도 같은 값이다`() {
        // 문서와 실측이 어긋나 있어 한쪽만 맞추면 표기가 바뀔 때 상권이 통째로 부재가 된다.
        assertEquals(CommercialActivityLevel.QUIET, CommercialActivityLevel.fromUpstreamOrNull("한산한 시간대"))
        assertEquals(CommercialActivityLevel.PEAK, CommercialActivityLevel.fromUpstreamOrNull("분주한 시간대"))
    }

    @Test
    fun `상권은 영문 병기가 붙어도 같은 값이다`() {
        // spec §8 8번의 미결 항목. 붙어 오든 아니든 같은 값으로 읽힌다.
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
