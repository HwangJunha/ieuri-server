package org.around.ieuri.auth.infrastructure.adapter.out.event

import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring ApplicationEventPublisher 기반 이벤트 발행 구현.
 * 추후 Kafka 발행으로 교체해도 애플리케이션 계층은 변경되지 않는다(포트 뒤에 있으므로).
 */
@Component
class SpringDomainEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : DomainEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
