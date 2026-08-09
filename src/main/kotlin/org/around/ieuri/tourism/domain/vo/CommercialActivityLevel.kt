package org.around.ieuri.tourism.domain.vo

/**
 * 상업 활성도 4단계(SPEC-0014 R10). 업스트림 `AREA_CMRCL_LVL`에 대한 식별자이며
 * [CongestionLevel]과는 독립적인 축이다 — 같은 구역에서 서로 다른 값일 수 있다.
 *
 * 121곳 중 82곳에만 실제 신호가 있다. **기준선이 없는 구역도 업스트림이 `보통 시간대`를
 * 고정 반환하므로**, 값만으로는 실측과 자리표시자를 구분할 수 없다 — [CommercialStatus]가
 * 그 구분을 진다.
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
        /**
         * 어간으로 대조한다. **업스트림이 `"한산한"`을 주는 것이 실측으로 확인됐다**
         * (2026-08-10 샘플 호출) — 매뉴얼과 spec은 `"한산한 시간대"`라고 적고 있어 서로
         * 다르다. 어느 쪽이 오더라도 같은 값으로 읽히도록 `시간대`와 영문 병기를 떼고
         * 남는 어간을 키로 쓴다.
         */
        private val BY_UPSTREAM = mapOf(
            "한산한" to QUIET,
            "보통" to NORMAL,
            "바쁜" to BUSY,
            "분주한" to PEAK,
        )

        /**
         * 업스트림 문자열 → 단계. 정의된 4값이 아니면 null이며, 호출자는 상권 정보를
         * 부재로 다룬다.
         *
         * `"한산한"` · `"한산한 시간대"` · `"한산한 시간대(Quiet Time)"`를 모두 받아들인다 —
         * 관측된 것은 첫 번째지만, 업스트림 표기가 문서와 어긋나 있어 한쪽만 맞추면
         * 표기가 바뀔 때 조용히 상권이 통째로 부재가 된다.
         */
        fun fromUpstreamOrNull(raw: String?): CommercialActivityLevel? {
            val stem = raw?.substringBefore('(')?.replace("시간대", "")?.trim() ?: return null
            return BY_UPSTREAM[stem]
        }
    }
}
