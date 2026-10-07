package org.around.ieuri.community.application.question.required

/** 장소 질문/답변 이벤트 발행 포트(Spring 이벤트). */
interface SpotQuestionEventPublisherPort {
    fun publish(event: Any)
}
