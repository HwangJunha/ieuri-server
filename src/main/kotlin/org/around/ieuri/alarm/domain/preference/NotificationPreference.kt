package org.around.ieuri.alarm.domain.preference

/** 회원 한 명의 카테고리 1건 수신 설정(SPEC-0028 R19). 행이 없으면 켜짐이다(AC10.3·AC19.1). */
data class NotificationPreference(
    val id: Long?,
    val memberId: Long,
    val category: NotificationCategory,
    val enabled: Boolean,
) {
    /** 같은 값으로 다시 설정해도 성공한다(AC19.5). */
    fun change(enabled: Boolean): NotificationPreference = copy(enabled = enabled)

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

        /** 모든 카테고리의 실제 값. 저장된 행이 없는 카테고리는 [DEFAULT_ENABLED], 순서는 [NotificationCategory] 선언 순이다. */
        fun effective(stored: List<NotificationPreference>): List<Pair<NotificationCategory, Boolean>> {
            val byCategory = stored.associateBy { it.category }
            return NotificationCategory.entries.map { it to (byCategory[it]?.enabled ?: DEFAULT_ENABLED) }
        }
    }
}
