package org.around.ieuri.alarm.domain.aggregate

import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.NotificationType

/** 알림 문구 1건(SPEC-0028 R13). 종류(`NotificationType`)와 언어의 조합마다 하나씩 있다. */
class NotificationMessage private constructor(
    val id: Long?,
    val type: NotificationType,
    val locale: AlarmLocale,
    val title: String,
    /** 서버가 정한 안내 문구(R13). 비우면 iOS가 알림을 조용히 버린다. */
    val body: String,
) {
    companion object {
        fun restore(
            id: Long,
            type: NotificationType,
            locale: AlarmLocale,
            title: String,
            body: String,
        ): NotificationMessage = NotificationMessage(
            id,
            type,
            locale,
            requireNotBlank(title, "알림 제목"),
            requireNotBlank(body, "알림 본문"),
        )

        private fun requireNotBlank(value: String, label: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "${label}은 비어 있을 수 없다." }
            return trimmed
        }
    }
}
