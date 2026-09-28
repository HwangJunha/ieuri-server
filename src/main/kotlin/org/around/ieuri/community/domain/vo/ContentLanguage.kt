package org.around.ieuri.community.domain.vo

/**
 * 요청 언어. 상단 칩 구성이 언어마다 다르므로 필요하다 — 카테고리의 가용 집합이 언어별로
 * 갈리기 때문이다(여행코스는 국문 전용, 교통은 외국어 전용).
 *
 * tourism의 DomainLocale과 값 집합이 같지만 별도로 둔다 — 도메인 간 내부 타입을 직접
 * 가져다 쓰지 않는 규칙 때문이며, 커뮤니티가 분리될 때 따라가야 하는 타입이기도 하다.
 */
enum class ContentLanguage {
    EN,
    KO,
    JA,
    ZH,
    ;

    companion object {
        /** 헤더 부재·미지원 언어 시 폴백. 외국인 방문객 우선. */
        val FALLBACK: ContentLanguage = EN

        /** BCP-47/ISO 언어 태그("ko-KR", "ja", "zh-Hant")를 도메인 언어로 정규화한다. */

        /**
         * `Accept-Language` 헤더를 콘텐츠 언어로 정규화한다. **첫 항목만 본다** —
         * 세밀한 q-값 협상 대신 클라이언트가 세션당 단일 언어를 설정하는 패턴에 맞춘다.
         */
        fun fromAcceptLanguage(header: String?): ContentLanguage = fromLanguageTag(
            header?.split(',')
                ?.firstOrNull()
                ?.substringBefore(';')
                ?.trim(),
        )

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
