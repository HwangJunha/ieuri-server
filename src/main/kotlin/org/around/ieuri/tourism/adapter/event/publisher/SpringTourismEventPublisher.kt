package org.around.ieuri.tourism.adapter.event.publisher

import org.around.ieuri.tourism.application.shared.required.TourismEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring ApplicationEventPublisher 기반 발행 구현. */
@Component
class SpringTourismEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : TourismEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
