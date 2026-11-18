package org.around.ieuri.tourism.domain.shared

/**
 * 구역명 4개 언어 표기(SPEC-0014). 중국어는 번체다.
 * - ja·zh는 번역이 없으면 null이다. 적재 시 EN으로 채우지 않는다.
 */
data class AreaName(
    val ko: String,
    val en: String,
    val ja: String?,
    val zh: String?,
) {
    /** 활성 언어의 이름. 없거나 공백뿐이면 EN → KO 순으로 폴백한다. */
    fun resolve(locale: DomainLocale): String {
        val preferred = when (locale) {
            DomainLocale.KO -> ko
            DomainLocale.EN -> en
            DomainLocale.JA -> ja
            DomainLocale.ZH -> zh
        }
        return preferred.orBlankFallback() ?: en.orBlankFallback() ?: ko
    }

    private fun String?.orBlankFallback(): String? = this?.takeIf { it.isNotBlank() }
}
