package org.around.ieuri.auth.application.port.out.event

/**
 * 도메인/애플리케이션 이벤트 발행 포트. 지금은 Spring ApplicationEventPublisher 구현,
 * 추후 Kafka로 교체 가능하도록 추상화한다. AFTER_COMMIT 후처리(검증메일 발송 등)를 트리거한다.
 */
interface DomainEventPublisherPort {
    fun publish(event: Any)
}
