package org.around.ieuri.community.application.messagereport.required

import org.around.ieuri.community.domain.messagereport.MessageReport

/** DM 신고 저장. 중복 판정은 신고자 단위의 유일 제약이 맡는다. */
interface MessageReportRepository {
    /** 신고자가 이미 신고한 메시지가 하나라도 있으면 false — 호출자가 예외를 던져 롤백한다. */
    fun insert(report: MessageReport): Boolean
}
