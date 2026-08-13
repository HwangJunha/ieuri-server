package org.around.ieuri.community.domain.vo

/**
 * 신고 대상 종류. 자동 숨김은 공개 콘텐츠([POST]·[COMMENT]·[FEED_ITEM])에만 적용되고
 * [MEMBER]는 기록만 된다 — [autoHideable]이 그 구분을 들고 있다.
 *
 * **DM 메시지는 여기 없다.** 다건 신고가 필요해 별도 애그리게이트(MessageReport)를 두었고,
 * 대상 타입이 메시지로 고정이라 열거값이 필요 없다(POL-049).
 */
enum class ReportTargetType {
    POST,
    COMMENT,
    MEMBER,
    FEED_ITEM,
    ;

    /**
     * 신고 누적으로 자동 숨김될 수 있는 대상인지.
     *
     * MEMBER가 false인 것이 이 도메인의 결정이다 — 신고 몇 건으로 한 사람의 글이 일괄로
     * 사라지면 조직적 신고가 곧 침묵 수단이 된다. 글 숨김은 되돌릴 수 있지만 사람을 지우는
     * 것은 그렇지 않다.
     */
    val autoHideable: Boolean get() = this != MEMBER
}

/** 신고 사유 고정 집합. [OTHER]는 상세 사유가 필수다. */
enum class ReportReason {
    SPAM,
    ABUSE,
    INAPPROPRIATE_CONTENT,
    OTHER,
    ;

    /** 상세 사유(detail)가 반드시 있어야 하는 사유인지. */
    val requiresDetail: Boolean get() = this == OTHER
}
