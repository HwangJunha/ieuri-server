package org.around.ieuri.community.application.shared.required

/** community 이벤트 발행 포트. */
interface CommunityEventPublisherPort {
    fun publish(event: Any)
}
