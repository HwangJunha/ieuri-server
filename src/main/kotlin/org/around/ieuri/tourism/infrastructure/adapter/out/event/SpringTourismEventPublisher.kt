package org.around.ieuri.tourism.infrastructure.adapter.out.event

import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring ApplicationEventPublisher 기반 발행 구현. auth 도메인의 `SpringDomainEventPublisher`와
 * 같은 관례다. Kafka로 교체해도 application 계층은 변경되지 않는다(포트 뒤에 있으므로).
 */
@Component
class SpringTourismEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : TourismEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
