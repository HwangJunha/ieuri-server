package org.around.ieuri.community.application.report.required

/** 신고 이벤트 발행 포트(Spring 이벤트). */
interface ReportEventPublisherPort {
    fun publish(event: Any)
}
