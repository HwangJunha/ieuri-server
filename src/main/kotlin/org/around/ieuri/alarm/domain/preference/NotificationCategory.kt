package org.around.ieuri.alarm.domain.preference

/** 알림 카테고리(SPEC-0028 R10·R19). 사용자가 끄고 켜는 단위다. */
enum class NotificationCategory {
    /** DM 새 메시지. */
    MESSAGE,

    /** 내 질문에 달린 답변, 내 답변에 달린 답글. */
    SPOT_QA,
}
