package org.around.ieuri.community.domain.report

/** 신고 대상 종류. DM 메시지 신고는 별도 애그리게이트(MessageReport)가 맡는다(POL-049). */
enum class ReportTargetType {
    POST,
    COMMENT,
    MEMBER,
    SPOT_QUESTION,
    SPOT_ANSWER,
    SPOT_ANSWER_REPLY,
    ;

    /** 신고 누적으로 자동 숨김될 수 있는 대상인지. [MEMBER]는 기록만 된다(POL-041). */
    val autoHideable: Boolean get() = this != MEMBER
}
