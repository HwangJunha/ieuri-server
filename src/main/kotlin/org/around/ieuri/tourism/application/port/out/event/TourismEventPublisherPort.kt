package org.around.ieuri.tourism.application.port.out.event

/** tourism 이벤트 발행 포트. 도메인마다 자기 발행 포트를 갖는다. */
interface TourismEventPublisherPort {
    fun publish(event: Any)
}
