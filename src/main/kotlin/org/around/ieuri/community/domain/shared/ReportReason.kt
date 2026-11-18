package org.around.ieuri.community.domain.shared

/** 신고 사유 고정 집합(POL-041). 글·댓글 신고와 DM 메시지 신고가 함께 쓴다. [OTHER]는 상세 사유가 필수다. */
enum class ReportReason {
    SPAM,
    ABUSE,
    INAPPROPRIATE_CONTENT,
    OTHER,
    ;

    val requiresDetail: Boolean get() = this == OTHER
}
