package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.domain.vo.RegionScoreFactor
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 한국관광공사 빅데이터 4종(SPEC-0015 §6) 접근 설정과 추천 점수 계수.
 *
 * **[TourApiProperties]에 얹지 않고 분리한다** — 서비스 경로가 셋으로 갈려 단일 base-url 구조로
 * 표현되지 않고(spec §4.7), 관광지 조회와 빅데이터가 한 설정을 공유하면 변경 영향이 서로 번진다.
 *
 * [serviceKey]는 4종 공용 단일 키이며 **환경변수로만 주입한다**(응답·로그·GraphQL 어디에도 노출
 * 금지). 사전 인코딩된 상태로 보관될 수 있어 URI 빌드 시 재인코딩하지 않는다.
 *
 * **아래 점수 계수는 전부 잠정값이다(§7.7 · POL-044).** 실측 근거가 없어 코드 상수로 박지 않고
 * 설정으로 뺐으나, 값이 없어도 앱이 기동해야 하므로 기본값을 둔다. 데이터가 쌓인 뒤 확정한다.
 *
 * @param baseUrls 서비스 키([SERVICE_DATA_LAB] 등)→base-url.
 * @param scoreWeights 항목별 가중치. 미지정 항목은 [org.around.ieuri.tourism.domain.vo.RegionScoreWeights.DEFAULT_WEIGHT].
 * @param distanceHalfLifeMeters 거리 감쇠 반감 거리(m). 이 거리에서 거리 점수가 50점이다.
 * @param freshnessPlateauDays / @param freshnessHalfLifeDays 지표 신선도 감쇠(방문자수 전용).
 * @param minFactorCount 추천 대상이 되기 위한 최소 산출 항목 수(R30). 경계값은 포함된다.
 * @param defaultLimit / @param maxLimit 추천 결과 기본 개수와 상한(R19 · POL-018). 초과는 클램프.
 */
@ConfigurationProperties(prefix = "ieuri.tour-stats")
data class TourStatsProperties(
    val serviceKey: String = "",
    val baseUrls: Map<String, String> = emptyMap(),
    val connectTimeoutMillis: Long = 5_000,
    val readTimeoutMillis: Long = 30_000,
    /**
     * 페이지당 행 수. **호출 수를 좌우한다** — 집중률은 시군구마다 호출해야 해서(252회) 페이지가
     * 갈리면 호출이 배가된다. 실측(2026-08-11) 지역별 최대가 제주시 7,320행이라 10,000이면
     * 전 지역이 1페이지에 끝나 252회다. 상한이 문서화된 값은 아니므로 페이징은 살려 둔다.
     */
    val pageSize: Int = 10_000,
    /**
     * 수요강도 지표 코드. **없으면 오류 없이 0건이 온다**(실측 2026-08-11) — 요청이 불완전한
     * 것을 "데이터 없음"으로 오해하기 쉽다. `21`=관광체류강도, `22`=관광소비강도이며 둘 다
     * "전체" 지표다. 세부 지표(2101 타권역 방문자 비중, 2102 숙박 비중 등)로 바꿀 수 있게
     * 설정으로 둔다.
     */
    val stayIndexCode: String = "21",
    val spendingIndexCode: String = "22",

    // --- 이하 잠정값(§7.7 실측 전) ---

    /**
     * 항목별 가중치. 사용자 판단이며 실측값이 아니다 — 균등 1.0 잠정값을 대체한다.
     * 집중률(예측이라 신뢰도가 높음)과 체류·소비 강도(높을수록 좋은 관광지)를 올렸다.
     */
    val scoreWeights: Map<RegionScoreFactor, Double> = emptyMap(),

    /**
     * 신선도 감쇠 유예 일수. 이 일수까지는 감쇠하지 않는다(계수 1.0).
     * 기본 7 — 방문자수가 주 단위로 들어와 일주일 지연은 정상 주기다.
     */
    val freshnessPlateauDays: Long = 7,

    /**
     * 신선도 반감기(일). 유예 이후 이 일수마다 계수가 절반이 된다.
     * 기본 23 — 유예 7일과 합쳐 30일 경과 시 정확히 0.5가 된다(사용자 지정 목표).
     */
    val freshnessHalfLifeDays: Double = 23.0,

    /**
     * 잠정: 30km. SPEC-0013 검색의 5km를 그대로 쓰지 않는다 — 그것은 spot 단위 실측값이고
     * 여기는 시군구 단위라 규모가 다르다(§3.3). 시군구 하나의 지름이 대략 이 규모다.
     */
    val distanceHalfLifeMeters: Double = 30_000.0,

    /** 잠정: 2. 항목 1개만으로 산출된 총점이 상위에 오는 것을 막는 최소선. */
    val minFactorCount: Int = 2,

    /** 잠정: 10 / 50. 목록 화면 한 스크롤 분량을 기준으로 잡았다. */
    val defaultLimit: Int = 10,
    val maxLimit: Int = 50,
) {
    companion object {
        /** 방문자수(locgoRegnVisitrDDList). */
        const val SERVICE_DATA_LAB = "DATA_LAB"

        /** 관광지 집중률(tatsCnctrRatedList). */
        const val SERVICE_TATS_CNCTR_RATE = "TATS_CNCTR_RATE"

        /** 체류·소비 강도(areaTarSjrnDsList · areaTarExpDsList). 두 오퍼레이션이 한 서비스에 있다. */
        const val SERVICE_AREA_TAR_DEM_DS = "AREA_TAR_DEM_DS"
    }
}
