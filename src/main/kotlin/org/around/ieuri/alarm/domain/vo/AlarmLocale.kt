package org.around.ieuri.alarm.domain.vo

/** 알림 문구의 언어(POL-022·POL-039). */
enum class AlarmLocale {
    EN,
    KO,
    JA,
    ZH,
    ;

    companion object {
        /** 대소문자 무시. 지원하지 않는 값·null·빈 값은 [EN]이다(AC20.3, POL-039). */
        fun ofOrFallback(raw: String?): AlarmLocale = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EN
    }
}
