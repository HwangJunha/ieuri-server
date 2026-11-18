package org.around.ieuri.community.application.post.required

/** 글 이벤트 발행 포트(Spring 이벤트). */
interface PostEventPublisherPort {
    fun publish(event: Any)
}
