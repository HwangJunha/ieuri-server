package org.around.ieuri.alarm.domain.push

import org.around.ieuri.alarm.domain.device.AlarmLocale

/**
 * 알림 문구 1건(SPEC-0028 R13). 종류(`NotificationType`)와 언어의 조합마다 하나씩 있는 읽기 전용 참조 값이다.
 * - 저장된 값은 [of]로 읽는다 — 앞뒤 공백을 걷고, 비어 있으면 실패한다. 본문을 비우면 iOS가 알림을 조용히 버린다.
 */
data class NotificationMessage(
    val type: NotificationType,
    val locale: AlarmLocale,
    val title: String,
    /** 서버가 정한 안내 문구(R13). */
    val body: String,
) {
    companion object {
        fun of(
            type: NotificationType,
            locale: AlarmLocale,
            title: String,
            body: String,
        ): NotificationMessage = NotificationMessage(
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
