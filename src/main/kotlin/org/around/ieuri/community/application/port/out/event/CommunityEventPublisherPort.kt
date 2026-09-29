package org.around.ieuri.community.application.port.out.event

/** community 이벤트 발행 포트. */
interface CommunityEventPublisherPort {
    fun publish(event: Any)
}
