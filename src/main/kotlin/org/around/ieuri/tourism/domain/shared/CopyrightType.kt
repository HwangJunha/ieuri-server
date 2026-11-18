package org.around.ieuri.tourism.domain.shared

/**
 * 공공누리 저작권 유형과 최소 출처 문구. 클라이언트가 유형별로 렌더링을 분기한다.
 * - UNSPECIFIED는 변경금지에 준해 취급한다.
 */
enum class CopyrightType(
    /** 최소 출처 문구. 항상 실제 출처를 담는다. */
    val attributionText: String,
) {
    /** 공공누리 제1유형 — 출처표시. 상업·변형 가능. */
    TYPE1("출처: 한국관광공사(공공누리 제1유형)"),

    /** 공공누리 제3유형 — 출처표시 + 변경금지. */
    TYPE3("출처: 한국관광공사(공공누리 제3유형·변경금지)"),

    /** 값 미제공. */
    UNSPECIFIED("출처: 한국관광공사"),
    ;

    companion object {
        /** 공공누리 코드 문자열(Type1/Type3, 대소문자·공백 무시)을 값으로. 그 외/빈 값은 UNSPECIFIED. */
        fun fromCode(code: String?): CopyrightType = when (code?.trim()?.lowercase()) {
            "type1" -> TYPE1
            "type3" -> TYPE3
            else -> UNSPECIFIED
        }
    }
}
