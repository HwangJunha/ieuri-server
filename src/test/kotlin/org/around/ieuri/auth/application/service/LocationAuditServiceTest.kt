package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.policy.LocationAuditPolicy
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 위치 조회 감사 기록(SPEC-0032 R19~R21, POL-057).
 *
 * 고정하는 계약은 셋이다 — **스위치가 꺼져 있으면 기록하지 않고**(R21),
 * **좌표를 어떤 형태로도 남기지 않으며**(R20·POL-045), **기록 실패가 요청을 실패시키지
 * 않는다**(R19).
 */
class LocationAuditServiceTest {

    private val memberId = 7L
    private val port = mockk<LocationAccessLogPort>(relaxed = true)

    private fun service(enabled: Boolean) =
        LocationAuditService(port, LocationAuditPolicy(enabled = enabled))

    // --- R21 스위치 ---

    /**
     * 신고 완료 전에는 코드가 배포돼도 동작하지 않는다 — 이것이 "미리 만들어 둔다"의 구현
     * 형태다.
     */
    @Test
    fun `스위치가 꺼져 있으면 기록하지 않는다`() {
        service(enabled = false).recordAccess(memberId, "spots")

        verify(exactly = 0) { port.record(any(), any()) }
    }

    @Test
    fun `스위치가 켜져 있으면 회원과 연산명을 남긴다`() {
        val payload = slot<Map<String, Any?>>()
        every { port.record(memberId, capture(payload)) } returns Unit

        service(enabled = true).recordAccess(memberId, "spots")

        verify(exactly = 1) { port.record(memberId, any()) }
        assertEquals("spots", payload.captured["operation"])
    }

    /** 비로그인은 남길 "누구"가 없다. */
    @Test
    fun `비로그인 호출은 기록하지 않는다`() {
        service(enabled = true).recordAccess(null, "spots")

        verify(exactly = 0) { port.record(any(), any()) }
    }

    // --- R20 좌표 미포함 ---

    /**
     * **이 테스트가 POL-045의 집행 장치다.** payload가 jsonb라 스키마가 막아 주지 않으므로,
     * 좌표를 뜻하는 어떤 키도 실리지 않는다는 것을 여기서 고정한다.
     *
     * 위도·경도는 물론 시군구·행정구역 어느 단위도 대상이다 — 위치의 *내용*이 빠져야
     * 이동 경로가 재구성되지 않는다.
     */
    @Test
    fun `기록에 좌표를 뜻하는 어떤 항목도 담기지 않는다`() {
        val payload = slot<Map<String, Any?>>()
        every { port.record(memberId, capture(payload)) } returns Unit

        service(enabled = true).recordAccess(memberId, "recommendedRegions")

        val forbidden = listOf(
            "lat", "latitude", "lng", "longitude", "coord", "coordinates",
            "district", "region", "area", "sigungu", "bbox", "radius",
        )
        val keys = payload.captured.keys.map { it.lowercase() }
        forbidden.forEach { banned ->
            assertTrue(
                keys.none { it.contains(banned) },
                "위치 감사 기록에 좌표성 항목이 들어갔다: $banned (payload=${payload.captured.keys})",
            )
        }
        // 담기는 것은 연산의 맥락뿐이다.
        assertEquals(setOf("operation"), payload.captured.keys)
    }

    // --- R19 기록 실패가 요청을 막지 않는다 ---

    /**
     * 감사 기록 실패로 `spots`가 실패하면 안 된다. 다만 조용히 버리지도 않는다 — 로그로 남는다.
     */
    @Test
    fun `기록이 실패해도 예외를 밖으로 내지 않는다`() {
        every { port.record(any(), any()) } throws RuntimeException("db down")

        service(enabled = true).recordAccess(memberId, "spots")
    }
}
