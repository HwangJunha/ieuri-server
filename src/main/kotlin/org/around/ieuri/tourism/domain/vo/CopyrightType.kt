package org.around.ieuri.tourism.domain.vo

/**
 * 공공누리 저작권 유형. TourAPI cpyrhtDivCd를 도메인 값으로 표현한다. 클라이언트가 유형별 렌더링을
 * 분기(TYPE3/UNSPECIFIED는 크롭 회피)하므로 서버가 값을 감추거나 기본값으로 뭉개지 않는다.
 * 빈 값(cpyrhtDivCd 부재)은 UNSPECIFIED로 두되, 보수적으로 변경금지에 준해 취급한다.
 */
enum class CopyrightType {
    /** 공공누리 제1유형 — 출처표시. 상업·변형 가능. */
    TYPE1,

    /** 공공누리 제3유형 — 출처표시 + 변경금지. */
    TYPE3,

    /** TourAPI가 값을 제공하지 않음. */
    UNSPECIFIED,
}
