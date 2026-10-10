package org.around.ieuri.auth.infrastructure.adapter.out.event

import org.around.ieuri.auth.application.member.required.DomainEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring ApplicationEventPublisher 기반 이벤트 발행 구현. */
@Component
class SpringDomainEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : DomainEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
