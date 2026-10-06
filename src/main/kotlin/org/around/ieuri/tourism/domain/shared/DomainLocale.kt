package org.around.ieuri.tourism.domain.shared

/**
 * 관광 콘텐츠 언어. TourAPI 언어별 서비스 선택과 좋아요/싫어요 기록의 언어 차원을 결정한다.
 * - 헤더 부재·파싱 불가 시 EN으로 폴백한다.
 */
enum class DomainLocale {
    EN,
    KO,
    JA,
    ZH,
    ;

    /** 국문 서비스 여부. 카테고리 비대칭(TRAVEL_COURSE vs TRANSPORT)과 코드 매핑 분기의 기준. */
    val isKorean: Boolean get() = this == KO

    companion object {
        /** 헤더 부재·미지원 언어 시 폴백. */
        val FALLBACK: DomainLocale = EN

        /**
         * `Accept-Language` 헤더를 도메인 언어로 정규화한다.
         * - 첫 항목만 본다(q-값 무시). 헤더 부재·파싱 불가는 폴백(EN)이다.
         */
        fun fromAcceptLanguage(header: String?): DomainLocale = fromLanguageTag(
            header?.split(',')
                ?.firstOrNull()
                ?.substringBefore(';')
                ?.trim(),
        )

        /**
         * BCP-47 언어 태그(예: "ko-KR", "zh-Hant")를 도메인 언어로 정규화한다. 지원 밖이면 폴백(EN).
         * - zh 계열은 전부 ZH(번체)로 수렴한다.
         */
        fun fromLanguageTag(tag: String?): DomainLocale {
            val primary = tag?.trim()?.substringBefore('-')?.lowercase() ?: return FALLBACK
            return when (primary) {
                "en" -> EN
                "ko" -> KO
                "ja" -> JA
                "zh" -> ZH
                else -> FALLBACK
            }
        }
    }
}
