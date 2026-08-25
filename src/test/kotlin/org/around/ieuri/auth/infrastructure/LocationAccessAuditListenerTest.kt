package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import org.around.ieuri.auth.infrastructure.adapter.`in`.event.LocationAccessAuditListener
import org.around.ieuri.common.event.LocationAccessed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 위치 조회 감사 기록(SPEC-0032 R19·R20, POL-057).
 *
 * 고정하는 계약은 둘이다 — **좌표를 어떤 형태로도 남기지 않고**(POL-045),
 * **기록 실패가 요청을 실패시키지 않는다**.
 */
class LocationAccessAuditListenerTest {

    private val memberId = 7L
    private val port = mockk<LocationAccessLogPort>(relaxed = true)
    private val listener = LocationAccessAuditListener(port)

    @Test
    fun `회원과 연산명을 남긴다`() {
        val payload = slot<Map<String, Any?>>()
        every { port.record(memberId, capture(payload)) } returns Unit

        listener.on(LocationAccessed(memberId, "spots"))

        verify(exactly = 1) { port.record(memberId, any()) }
        assertEquals("spots", payload.captured["operation"])
    }

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

        listener.on(LocationAccessed(memberId, "recommendedRegions"))

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

    /** 감사 기록 실패로 `spots`가 실패하면 안 된다. 다만 조용히 버리지도 않는다 — 로그로 남는다. */
    @Test
    fun `기록이 실패해도 예외를 밖으로 내지 않는다`() {
        every { port.record(any(), any()) } throws RuntimeException("db down")

        listener.on(LocationAccessed(memberId, "spots"))
    }
}
