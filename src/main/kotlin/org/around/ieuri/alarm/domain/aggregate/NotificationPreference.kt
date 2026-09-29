package org.around.ieuri.alarm.domain.aggregate

import org.around.ieuri.alarm.domain.vo.NotificationCategory

/** 회원 한 명의 카테고리 1건 수신 설정(SPEC-0028 R19). 행이 없으면 켜짐이다(AC10.3·AC19.1). */
class NotificationPreference private constructor(
    val id: Long?,
    val memberId: Long,
    val category: NotificationCategory,
    enabled: Boolean,
) {
    var enabled: Boolean = enabled
        private set

    /** 같은 값으로 다시 설정해도 성공한다(AC19.5). */
    fun change(enabled: Boolean) {
        this.enabled = enabled
    }

    companion object {
        /** 저장된 행이 없을 때의 값. 두 카테고리 모두 켜짐이다. */
        const val DEFAULT_ENABLED = true

        fun create(
            memberId: Long,
            category: NotificationCategory,
            enabled: Boolean,
        ): NotificationPreference {
            require(memberId > 0) { "memberId는 양수여야 한다." }
            return NotificationPreference(null, memberId, category, enabled)
        }

        fun restore(
            id: Long,
            memberId: Long,
            category: NotificationCategory,
            enabled: Boolean,
        ): NotificationPreference = NotificationPreference(id, memberId, category, enabled)
    }
}
