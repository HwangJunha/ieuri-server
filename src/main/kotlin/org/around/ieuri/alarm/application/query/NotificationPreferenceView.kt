package org.around.ieuri.alarm.application.query

import org.around.ieuri.alarm.domain.vo.NotificationCategory

/**
 * 카테고리 1건의 수신 설정 읽기 모델(SPEC-0028 R19).
 *
 * 조회는 **저장된 행과 무관하게 항상 두 카테고리를 채워** 돌려준다 — 행이 없는 회원도
 * 켜짐으로 온다(AC19.1). 클라이언트가 초기화 호출을 따로 하지 않아도 되는 이유다.
 */
data class NotificationPreferenceView(
    val category: NotificationCategory,
    val enabled: Boolean,
)
