package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import java.time.LocalDate

/**
 * recommendedRegions 쿼리 입력(SPEC-0015 R19·R21). 언어는 인자가 아니라 Accept-Language에서 온다.
 *
 * [origin]은 **필수다**(R21) — 위치 없이는 "여기 말고 근처의 다른 곳"이 성립하지 않는다.
 * [limit]은 어댑터가 이미 검증·클램프한 값이라 여기서는 양수 상한 이하임이 보장된다.
 */
data class RecommendedRegionQuery(
    val origin: Coordinates,
    val limit: Int,
)

/**
 * recommendedRegions 응답(R16·R20).
 *
 */
data class RecommendedRegionView(
    val regions: List<RecommendedRegionItemView>,
)

/**
 * 추천 시군구 1건(R17·R18·R31).
 *
 * [scores]에는 **산출 가능했던 항목만** 들어간다 — 부재 항목은 0점으로 채우지 않고 빠진다
 * (POL-043). [scoreFactorCount]가 그 개수이며, 지역마다 다를 수 있다는 사실을 감추지 않는다.
 *
 * **총점은 여기 없다.** 내부에서는 정렬 키로 계속 쓰지만(R16) 계약에 내보내지 않는다 —
 * 가중평균이라 값이 항상 항목 점수 사이에 놓여, 1위 지역이 60점으로 보이는 일이 생긴다.
 * 추천 순위는 목록 순서가, 항목별 상대 위치는 [RegionScoreView.rank]가 전달한다.
 */
data class RecommendedRegionItemView(
    val code: String,
    val name: String,
    val provinceName: String,
    val scores: List<RegionScoreView>,
    val scoreFactorCount: Int,
    val center: Coordinates,
)

/**
 * 항목 점수 1개와 그 근거 데이터의 기준일, 그리고 그 항목에서의 순위.
 *
 * **기준일도 순위도 도메인 [RegionScore]에 넣지 않는다.** 언제 측정된 값인지는 표시용 출처
 * 정보이고, 순위는 한 지역이 아니라 **후보 집합 전체**에서만 정해지는 값이다 — 둘 중 어느
 * 것도 한 지역의 점수 산출 규칙이 아니다.
 *
 * [basisAt]의 null은 기준일을 알 수 없다는 뜻이다. 요청 시점에 계산되는 DISTANCE가 그렇다 —
 * 적재된 데이터가 아니라 기준일이라는 개념 자체가 없다.
 *
 * [rank]는 **1이 가장 좋고**(= [RegionScore.value]가 가장 높음), 상한을 적용하기 전의 후보
 * 전체에서 매겨진다. 동점은 같은 순위를 받고 다음 순위를 건너뛴다(표준 경쟁 순위).
 */
data class RegionScoreView(
    val score: RegionScore,
    val basisAt: LocalDate?,
    val rank: Int,
) {
    init {
        require(rank >= 1) { "항목 순위는 1 이상이어야 합니다: $rank" }
    }
}

/**
 * 순위가 붙기 전의 항목 점수 — 순위는 후보 집합이 확정된 뒤에야 정해진다.
 *
 * [RegionScoreView]를 임시값으로 먼저 만들면 순위를 못 붙인 인스턴스가 밖으로 샐 수 있어,
 * 순위 이전 단계를 아예 다른 타입으로 둔다.
 */
data class ScoredFactor(
    val score: RegionScore,
    val basisAt: LocalDate?,
)

/**
 * 추천 후보 시군구 1건. 지표는 여기 없다 — 지표별 질의가 코드로 따로 붙인다.
 *
 * [center]는 기하학적 중심이 아니라 배치가 골라 둔 **대표 위치**다(설계 §11). 대표 위치가
 * 없는 시군구는 애초에 후보에서 빠지므로 이 값은 항상 존재한다.
 *
 * [provinceCode]는 [code]의 앞 2자리와 같은 값이지만 별도 컬럼에서 온다 — 후보를 같은 시도로
 * 좁히고 시군구가 적은 시도를 빼는 데 쓴다.
 */
data class RegionRow(
    val code: String,
    val provinceCode: String,
    val name: AreaName,
    val provinceName: AreaName,
    val center: Coordinates,
)

/**
 * origin 좌표가 실제로 들어앉은 시군구. 경계 안에 있는 지역이 없으면 이 값 자체가 null이며
 * (바다·해외) 그때 추천 결과는 빈 목록이다 — 오류가 아니다.
 *
 * 추천에서 이 지역은 빠지고([code]), 조회 자체가 이 시도로 좁혀진다([provinceCode]).
 */
data class OriginDistrict(
    val code: String,
    val provinceCode: String,
)

/**
 * 한 시군구와 그 지역에서 **실제로 값이 있었던** 지표들(R14 · POL-043).
 *
 * 지역 전건과 지표별 맵을 코드에서 결합한 결과다. 결측 지표는 [metrics]에 **키 자체가 없다** —
 * 0으로 채우지 않는다. 0으로 채우면 데이터 부재가 낮은 평가로 둔갑해 신규·소규모 시군구가
 * 부당하게 밀린다. 기준일이 지표마다 따로 실리는 것은 항목별
 * `basisAt`이 **실제로 쓰인 데이터**의 시점이어야 하기 때문이다.
 */
data class RegionMetricRow(
    val region: RegionRow,
    /**
     * 항목 → (원본 값, 기준일). **값이 있는 항목만** 들어간다(R14).
     *
     * 정규화 구간 산출·점수 산출·기준일 표기가 전부 이 하나를 본다 — 목록이 갈리면 한쪽에만
     * 추가된 항목이 조용히 사라진다. 거리는 여기 없다: 적재된 값이 아니라 요청 시점 계산이다.
     */
    val metrics: Map<RegionScoreFactor, RegionMetric>,
) {
    val code: String get() = region.code
    val provinceCode: String get() = region.provinceCode
    val name: AreaName get() = region.name
    val provinceName: AreaName get() = region.provinceName
    val center: Coordinates get() = region.center

    /** 이 행에 실제로 값이 있는 지표의 기준일들. 하나도 없으면 비어 있다. */
    val basisDates: List<LocalDate>
        get() = metrics.mapNotNull { (_, metric) -> metric.basisDate }
}

/**
 * 한 항목의 정규화 이전 원본 값과 그 근거 데이터의 기준일.
 *
 * [basisDate]의 null은 값은 있는데 기준일을 모른다는 뜻이다 — 이때 신선도 감쇠는 적용하지
 * 않는다(나이를 알 수 없어 임의로 깎을 근거가 없다).
 *
 * [ageDays]는 기준일로부터 오늘까지의 일수이며 **DB가 한국 날짜로 계산해 넘긴다** — 앱에서
 * 재면 앱 시계와 질의의 `오늘`이 서로 다른 날을 가리킬 수 있다(DB가 UTC라 한국 00~09시
 * 구간에서 하루가 어긋난다). 신선도 감쇠를 쓰는 방문자수만 채우고 나머지는 null이다.
 */
data class RegionMetric(
    val rawValue: Double,
    val basisDate: LocalDate?,
    val ageDays: Long? = null,
)
