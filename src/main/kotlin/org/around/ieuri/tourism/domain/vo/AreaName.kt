package org.around.ieuri.tourism.domain.vo

/**
 * 구역명 4개 언어 표기(SPEC-0014 R31). EN은 서울시 원본(`ENG_NM`)이고 JA/ZH는 spec에서
 * 작성한 초안이며 중국어는 번체다.
 *
 * ja·zh가 nullable인 것은 서울시가 구역을 추가했을 때 번역 없이 먼저 들어올 수 있기
 * 때문이다. 적재 시 빈 칸을 EN으로 채우지 않는다 — 채우면 실제 번역이 들어왔는지
 * 구분할 수 없다.
 */
data class AreaName(
    val ko: String,
    val en: String,
    val ja: String?,
    val zh: String?,
) {
    /**
     * 활성 언어의 이름. 없으면 **EN → KO** 순으로 폴백한다(R31).
     *
     * 공백만 있는 값도 부재로 본다 — 적재 사고로 빈 문자열이 들어오면 마커에 이름이
     * 사라지는데, 다른 언어로라도 보이는 편이 낫다.
     */
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
