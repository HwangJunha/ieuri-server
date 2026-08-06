package org.around.ieuri.community.application.port.out.event

/**
 * community 이벤트 발행 포트. auth의 `DomainEventPublisherPort`·tourism의
 * `TourismEventPublisherPort`와 같은 형태이며, 도메인 간 내부 참조 금지 규칙 때문에
 * 각 도메인이 자기 포트를 갖는다.
 */
interface CommunityEventPublisherPort {
    fun publish(event: Any)
}
