package org.around.ieuri.community.adapter.event.publisher

import org.around.ieuri.community.application.conversation.required.ConversationEventPublisherPort
import org.around.ieuri.community.application.post.required.PostEventPublisherPort
import org.around.ieuri.community.application.question.required.SpotQuestionEventPublisherPort
import org.around.ieuri.community.application.report.required.ReportEventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Spring ApplicationEventPublisher 기반 발행 구현. community 애그리게이트의 발행 포트를 모두 맡는다. */
@Component
class SpringCommunityEventPublisher(
    private val publisher: ApplicationEventPublisher,
) : PostEventPublisherPort,
    SpotQuestionEventPublisherPort,
    ReportEventPublisherPort,
    ConversationEventPublisherPort {

    override fun publish(event: Any) {
        publisher.publishEvent(event)
    }
}
