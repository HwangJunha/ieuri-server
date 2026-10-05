package org.around.ieuri.community.adapter.event.publisher

import org.around.ieuri.community.application.shared.required.CommunityEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring ApplicationEventPublisher 기반 발행 구현. */
@Component
class SpringCommunityEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : CommunityEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
