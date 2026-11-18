package org.around.ieuri.tourism.domain.congestion

/**
 * 인구 혼잡도 4단계(SPEC-0014 R7). 업스트림 한국어 문자열을 그대로 노출하지 않는다(POL-023).
 * - 최근 28일 평균 대비 비율이며 절대 인원수가 아니다.
 */
enum class CongestionLevel {
    /** 여유 — 평균의 50% 이하. */
    RELAXED,

    /** 보통 — 50% 초과 75% 이하. */
    NORMAL,

    /** 약간 붐빔 — 75% 초과 100% 이하. */
    SLIGHTLY_CROWDED,

    /** 붐빔 — 100% 초과. */
    CROWDED,
    ;

    companion object {
        private val BY_UPSTREAM = mapOf(
            "여유" to RELAXED,
            "보통" to NORMAL,
            "약간 붐빔" to SLIGHTLY_CROWDED,
            "붐빔" to CROWDED,
        )

        /** 업스트림 문자열 → 단계. 정의된 4값이 아니면 null이다(R7) — 호출자가 그 구역을 건너뛴다. */
        fun fromUpstreamOrNull(raw: String?): CongestionLevel? = BY_UPSTREAM[raw?.trim() ?: return null]
    }
}
