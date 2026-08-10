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
 * @param minFactorCount 추천 대상이 되기 위한 최소 산출 항목 수(R30). 경계값은 포함된다.
 * @param defaultLimit / @param maxLimit 추천 결과 기본 개수와 상한(R19 · POL-018). 초과는 클램프.
 */
@ConfigurationProperties(prefix = "ieuri.kto-bigdata")
data class KtoBigDataProperties(
    val serviceKey: String = "",
    val baseUrls: Map<String, String> = emptyMap(),
    val connectTimeoutMillis: Long = 5_000,
    val readTimeoutMillis: Long = 30_000,
    /** 페이지당 행 수. 1일 방문자수가 시군구 740행이라 1,000이면 대부분 1페이지에 끝난다. */
    val pageSize: Int = 1_000,

    // --- 이하 잠정값(§7.7 실측 전) ---

    /** 잠정: 다섯 항목 균등 가중. 변별력을 모르는 상태에서 특정 항목을 편애할 근거가 없다. */
    val scoreWeights: Map<RegionScoreFactor, Double> = emptyMap(),

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
