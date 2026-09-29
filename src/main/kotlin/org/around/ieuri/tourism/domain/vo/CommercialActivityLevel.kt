package org.around.ieuri.tourism.domain.vo

/**
 * 상업 활성도 4단계(SPEC-0014). 업스트림 `AREA_CMRCL_LVL`이며 [CongestionLevel]과 독립이다.
 * - 값만으로는 실측과 자리표시자를 구분할 수 없다 — [CommercialStatus]가 그 구분을 진다.
 */
enum class CommercialActivityLevel {
    /** 한산한 시간대. */
    QUIET,

    /** 보통 시간대. 실측일 수도, 기준선 부재의 자리표시자일 수도 있다. */
    NORMAL,

    /** 바쁜 시간대. */
    BUSY,

    /** 분주한 시간대. */
    PEAK,
    ;

    companion object {
        /** `시간대`와 영문 병기를 뗀 어간으로 대조한다. */
        private val BY_UPSTREAM = mapOf(
            "한산한" to QUIET,
            "보통" to NORMAL,
            "바쁜" to BUSY,
            "분주한" to PEAK,
        )

        /**
         * 업스트림 문자열 → 단계. 정의된 4값이 아니면 null이다.
         * - `"한산한"` · `"한산한 시간대"` · `"한산한 시간대(Quiet Time)"`를 모두 받아들인다.
         */
        fun fromUpstreamOrNull(raw: String?): CommercialActivityLevel? {
            val stem = raw?.substringBefore('(')?.replace("시간대", "")?.trim() ?: return null
            return BY_UPSTREAM[stem]
        }
    }
}
