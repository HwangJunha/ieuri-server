package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScore
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
    val scores: List<RegionScore>,
    val scoreFactorCount: Int,
    val center: Coordinates,
)

/**
 * 한 시군구의 원본 지표 행(R14 · POL-043). 아웃바운드 질의 포트가 돌려주는 정규화 이전의 값이다.
 *
 * **네 지표가 전부 nullable이며 null은 결측이다.** 0으로 채우지 않는다 — 데이터 부재가 낮은
 * 평가로 둔갑해 신규·소규모 시군구가 부당하게 밀린다. 각 지표의 기준일이 함께 오는 것은
 * 응답의 `dataBasisAt`(R20)이 **실제로 쓰인 데이터**의 시점이어야 하기 때문이다.
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
    val stayIntensity: Double?,
    val stayBasisMonth: LocalDate?,
    val spendingIntensity: Double?,
    val spendingBasisMonth: LocalDate?,
) {
    /** 이 행에 실제로 값이 있는 지표의 기준일들. 하나도 없으면 비어 있다. */
    val basisDates: List<LocalDate>
        get() = listOfNotNull(
            concentrationBasisDate.takeIf { concentrationRate != null },
            visitorBasisDate.takeIf { visitorCount != null },
            stayBasisMonth.takeIf { stayIntensity != null },
            spendingBasisMonth.takeIf { spendingIntensity != null },
        )
}
