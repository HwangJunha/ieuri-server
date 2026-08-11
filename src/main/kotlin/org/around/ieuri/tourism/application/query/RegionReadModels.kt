package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * recommendedRegions 쿼리 입력(SPEC-0015 R19·R21). 언어는 인자가 아니라 Accept-Language에서 온다.
 *
 * [origin]의 null은 정상 경로다 — 거리 항목 없이 나머지로 산출한다(R21). [limit]은 어댑터가
 * 이미 검증·클램프한 값이므로 여기서는 양수 상한 이하임이 보장된다.
 */
data class RecommendedRegionQuery(
    val origin: Coordinates?,
    val limit: Int,
)

/**
 * recommendedRegions 응답(R16·R20).
 *
 * [dataBasisAt]의 **null은 "적재된 데이터가 하나도 없다"** 는 뜻이며, 이때 [regions]도 반드시
 * 비어 있다. 데이터는 적재됐으나 추천 조건(최소 항목 수)을 만족한 지역이 없으면 [regions]가
 * 비어 있어도 [dataBasisAt]은 채워진다 — 두 상태는 다르다.
 */
data class RecommendedRegionView(
    val regions: List<RecommendedRegionItemView>,
    val dataBasisAt: LocalDateTime?,
)

/**
 * 추천 시군구 1건(R17·R18·R31).
 *
 * [scores]에는 **산출 가능했던 항목만** 들어간다 — 부재 항목은 0점으로 채우지 않고 빠진다
 * (POL-043). [scoreFactorCount]가 그 개수이며, 지역마다 다를 수 있다는 사실을 감추지 않는다.
 */
data class RecommendedRegionItemView(
    val code: String,
    val name: String,
    val provinceName: String,
    val totalScore: Double,
    val scores: List<RegionScoreView>,
    val scoreFactorCount: Int,
    val center: Coordinates,
)

/**
 * 항목 점수 1개와 그 근거 데이터의 기준일.
 *
 * **기준일을 도메인 [RegionScore]에 넣지 않는다.** 언제 측정된 값인지는 점수 산출 규칙이
 * 아니라 표시용 출처 정보다 — 도메인 VO에 넣으면 점수 계산이 날짜에 의존하는 것처럼 읽힌다.
 *
 * [basisAt]의 null은 기준일을 알 수 없다는 뜻이다. 요청 시점에 계산되는 DISTANCE가 그렇다 —
 * 적재된 데이터가 아니라 기준일이라는 개념 자체가 없다.
 */
data class RegionScoreView(
    val score: RegionScore,
    val basisAt: LocalDate?,
)

/**
 * 한 시군구의 원본 지표 행(R14 · POL-043). 아웃바운드 질의 포트가 돌려주는 정규화 이전의 값이다.
 *
 * **네 지표가 전부 nullable이며 null은 결측이다.** 0으로 채우지 않는다 — 데이터 부재가 낮은
 * 평가로 둔갑해 신규·소규모 시군구가 부당하게 밀린다. 각 지표의 기준일이 함께 오는 것은
 * 응답의 `dataBasisAt`(R20)과 항목별 `basisAt`이 **실제로 쓰인 데이터**의 시점이어야 하기
 * 때문이다.
 *
 * [visitorCount]는 하루치가 아니라 **최근 7일 평균이며 외지인+외국인만 합산한 값이다.**
 * 하루만 보면 요일에 따라 40%씩 흔들리고(실측: 강남구 금 1,312,557 vs 일 939,745), 현지인을
 * 포함하면 "사람이 사는 곳"이 추천된다(실측: 제주시 현지인 487,659 vs 외지인 146,084).
 * [visitorBasisDate]는 그 7일 중 가장 최근 기준일이다 — 신선도 감쇠가 이 날짜를 쓴다.
 */
data class RegionMetricRow(
    val code: String,
    val name: AreaName,
    val provinceName: AreaName,
    val center: Coordinates,
    val concentrationRate: Double?,
    val concentrationBasisDate: LocalDate?,
    val visitorCount: Double?,
    val visitorBasisDate: LocalDate?,
    /**
     * 방문자수 기준일로부터 오늘까지의 일수. **DB가 한국 날짜로 계산해 넘긴다** — 앱에서 재면
     * 앱 시계와 질의의 `오늘`이 서로 다른 날을 가리킬 수 있다(DB가 UTC라 한국 00~09시 구간에서
     * 하루가 어긋난다). 신선도 감쇠가 이 값을 쓴다.
     */
    val visitorAgeDays: Long? = null,
    val stayIntensity: Double?,
    val stayBasisMonth: LocalDate?,
    val spendingIntensity: Double?,
    val spendingBasisMonth: LocalDate?,
) {
    /** 이 행에 실제로 값이 있는 지표의 기준일들. 하나도 없으면 비어 있다. */
    val basisDates: List<LocalDate>
        get() = metrics.mapNotNull { (_, metric) -> metric.basisDate }

    /**
     * 항목 → (원본 값, 기준일). **값이 있는 항목만** 들어간다(R14).
     *
     * 정규화 구간 산출·점수 산출·기준일 표기가 전부 이 하나를 보게 묶어 둔다 — 목록이 갈리면
     * 한쪽에만 추가된 항목이 조용히 사라진다. 거리는 여기 없다: 적재된 값이 아니라 요청
     * 시점에 계산되기 때문이다.
     */
    val metrics: Map<RegionScoreFactor, RegionMetric>
        get() = buildMap {
            putMetric(RegionScoreFactor.CONGESTION, concentrationRate, concentrationBasisDate)
            putMetric(RegionScoreFactor.VISITOR_VOLUME, visitorCount, visitorBasisDate)
            putMetric(RegionScoreFactor.STAY_INTENSITY, stayIntensity, stayBasisMonth)
            putMetric(RegionScoreFactor.SPENDING_INTENSITY, spendingIntensity, spendingBasisMonth)
        }

    private fun MutableMap<RegionScoreFactor, RegionMetric>.putMetric(
        factor: RegionScoreFactor,
        value: Double?,
        basisDate: LocalDate?,
    ) {
        if (value != null) put(factor, RegionMetric(value, basisDate))
    }
}

/**
 * 한 항목의 정규화 이전 원본 값과 그 근거 데이터의 기준일.
 *
 * [basisDate]의 null은 값은 있는데 기준일을 모른다는 뜻이다 — 이때 신선도 감쇠는 적용하지
 * 않는다(나이를 알 수 없어 임의로 깎을 근거가 없다).
 */
data class RegionMetric(
    val rawValue: Double,
    val basisDate: LocalDate?,
)
