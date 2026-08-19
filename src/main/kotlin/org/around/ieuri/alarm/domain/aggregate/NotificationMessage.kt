package org.around.ieuri.alarm.domain.aggregate

import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.NotificationType

/**
 * 알림 제목 문구 1건(SPEC-0028 R13).
 *
 * **푸시에 본문을 싣지 않으므로 이 제목이 알림의 유일한 내용이다.** 그래서 문구를 다듬는
 * 일이 실제로 생기고, 배포 없이 고칠 수 있도록 DB에 둔다.
 *
 * 종류(`NotificationType`)와 언어의 조합마다 하나씩 있다 — 카테고리가 아니라 종류인 것은
 * 답변과 답글이 한 카테고리이면서 다른 문구를 갖기 때문이다.
 */
class NotificationMessage private constructor(
    val id: Long?,
    val type: NotificationType,
    val locale: AlarmLocale,
    /** **`val`이다** — 앱에 쓰기 경로가 없다. 값은 마이그레이션이 넣고 운영자가 DB에서 고친다. */
    val title: String,
) {
    companion object {
        fun restore(
            id: Long,
            type: NotificationType,
            locale: AlarmLocale,
            title: String,
        ): NotificationMessage = NotificationMessage(id, type, locale, requireValidTitle(title))

        private fun requireValidTitle(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "알림 제목은 비어 있을 수 없다." }
            return trimmed
        }
    }
}
