package org.around.ieuri.community.infrastructure.adapter.out.event

import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring ApplicationEventPublisher 기반 발행 구현. auth·tourism과 같은 관례다.
 */
@Component
class SpringCommunityEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : CommunityEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
