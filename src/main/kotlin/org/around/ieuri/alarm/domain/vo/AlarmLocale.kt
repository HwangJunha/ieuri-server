package org.around.ieuri.alarm.domain.vo

/**
 * 알림 문구의 언어(POL-022·POL-039).
 *
 * tourism의 `Locale`과 값이 같지만 **타입을 빌려 오지 않는다** — 도메인은 다른 도메인의
 * 내부에 의존하지 않는다. GraphQL 계약은 같은 `Locale` enum 하나를 쓰고, 어댑터가 이쪽으로
 * 옮긴다.
 */
enum class AlarmLocale {
    EN,
    KO,
    JA,
    ZH,
    ;

    companion object {
        /**
         * 지원하지 않는 값은 [EN]으로 떨어뜨린다(AC20.3, POL-039의 폴백 규약).
         * 대소문자를 가리지 않으며, null·빈 값도 [EN]이다.
         */
        fun ofOrFallback(raw: String?): AlarmLocale =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EN
    }
}
