package org.around.ieuri.tourism.adapter.event.publisher

import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.recommendation.required.RecommendationEventPublisherPort
import org.around.ieuri.tourism.application.region.required.RegionEventPublisherPort
import org.around.ieuri.tourism.application.spot.required.SpotEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring ApplicationEventPublisher 기반 발행 구현. tourism 애그리게이트의 발행 포트를 모두 맡는다. */
@Component
class SpringTourismEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : SpotEventPublisherPort,
    CatalogEventPublisherPort,
    RecommendationEventPublisherPort,
    RegionEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
