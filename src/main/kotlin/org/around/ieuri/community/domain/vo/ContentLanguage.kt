package org.around.ieuri.community.domain.vo

/** 요청 언어. 카테고리 가용 집합이 언어별로 갈린다. */
enum class ContentLanguage {
    EN,
    KO,
    JA,
    ZH,
    ;

    companion object {
        /** 헤더 부재·미지원 언어 시 폴백. 외국인 방문객 우선. */
        val FALLBACK: ContentLanguage = EN

        /** `Accept-Language` 헤더를 콘텐츠 언어로 정규화한다. 첫 항목만 보고 q-값은 무시한다. */
        fun fromAcceptLanguage(header: String?): ContentLanguage = fromLanguageTag(
            header?.split(',')
                ?.firstOrNull()
                ?.substringBefore(';')
                ?.trim(),
        )

        /** BCP-47/ISO 언어 태그("ko-KR", "ja", "zh-Hant")를 도메인 언어로 정규화한다. */
        fun fromLanguageTag(tag: String?): ContentLanguage {
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
