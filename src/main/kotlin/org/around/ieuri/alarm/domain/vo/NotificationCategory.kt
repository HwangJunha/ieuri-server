package org.around.ieuri.alarm.domain.vo

/**
 * 알림 카테고리(SPEC-0028 R10·R19).
 *
 * 트리거 3종을 두 값으로 묶는다 — 답변과 답글은 사용자에게 같은 성격이고 목적지도 같은
 * 화면이라 따로 끄고 켤 이유가 없다.
 */
enum class NotificationCategory {
    /** DM 새 메시지. */
    MESSAGE,

    /** 내 질문에 달린 답변, 내 답변에 달린 답글. */
    SPOT_QA,
}
