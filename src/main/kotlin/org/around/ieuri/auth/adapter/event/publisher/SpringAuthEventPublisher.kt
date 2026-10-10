package org.around.ieuri.auth.adapter.event.publisher

import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.token.required.TokenEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring 이벤트로 내보낸다. 회원·토큰 두 발행 포트를 함께 구현한다. */
@Component
class SpringAuthEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : MemberEventPublisherPort,
    TokenEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
