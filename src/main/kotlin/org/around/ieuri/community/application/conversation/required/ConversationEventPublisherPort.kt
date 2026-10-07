package org.around.ieuri.community.application.conversation.required

/** 대화 이벤트 발행 포트(Spring 이벤트). */
interface ConversationEventPublisherPort {
    fun publish(event: Any)
}
