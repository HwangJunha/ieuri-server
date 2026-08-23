package org.around.ieuri.tourism.application.port.out.event

/**
 * tourism 이벤트 발행 포트. auth 도메인의 `DomainEventPublisherPort`와 같은 형태이며,
 * 도메인 간 내부 참조 금지 규칙 때문에 각 도메인이 자기 포트를 갖는다.
 *
 * 지금은 Spring ApplicationEventPublisher 구현이고 Kafka 이관은 후속이다(POL-028) —
 * 그때도 이 포트 뒤에서 구현체만 바뀐다.
 */
interface TourismEventPublisherPort {
    fun publish(event: Any)
}
