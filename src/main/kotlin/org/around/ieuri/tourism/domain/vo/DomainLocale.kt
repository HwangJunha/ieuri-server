package org.around.ieuri.tourism.domain.vo

/**
 * 관광 콘텐츠 언어. TourAPI 언어별 서비스(KorService2/EngService2/JpnService2/ChtService2) 선택과
 * 좋아요/싫어요 기록의 언어 차원을 결정한다. 헤더 부재/파싱불가 시 EN으로 폴백한다(외국인 방문객 우선).
 *
 * 도메인 계층에 두는 이유: TourAPI 필드명·엔드포인트를 도메인이 알 필요는 없으나, "어떤 언어로 판단이
 * 기록되었는가"는 저장 모델(PK 구성)의 도메인 개념이므로 순수 enum으로 소유한다.
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
         * `Accept-Language` 헤더를 도메인 언어로 정규화한다(RFC 9110 언어 협상).
         *
         * **첫 항목만 본다** — 세밀한 q-값 협상 대신, 클라이언트가 세션당 단일 언어를 설정하는
         * 사용 패턴에 맞춘다. 헤더 부재·파싱 불가는 폴백(EN)이다.
         */
        fun fromAcceptLanguage(header: String?): DomainLocale = fromLanguageTag(
            header?.split(',')
                ?.firstOrNull()
                ?.substringBefore(';')
                ?.trim(),
        )

        /**
         * BCP-47/ISO 언어 태그(예: "ko-KR", "ja", "zh-Hant")를 도메인 언어로 정규화한다.
         * 지원 밖이면 폴백(EN). 중국어는 번체(ChtService2)만 지원하므로 zh 계열은 ZH로 수렴한다.
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
