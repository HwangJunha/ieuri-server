package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 추천 목록 설정(SPEC-0035 §9 · POL-044). 코드 기본값은 스펙 §9의 **출발값**이며 실측 후 확정한다(T1·T7).
 * 값은 `application.yaml`이 덮어쓴다.
 */
@ConfigurationProperties(prefix = "ieuri.tourism.recommendation")
data class RecommendationProperties(
    val score: Score = Score(),
    val dpp: Dpp = Dpp(),
    val cursor: Cursor = Cursor(),
    val cache: Cache = Cache(),
) {
    /** 점수 요인(R6~R12). */
    data class Score(
        val weights: Weights = Weights(),
        /** 거리 요인이 0.5가 되는 거리(m). SPEC-0013 실측 5km. */
        val distanceHalfLifeMeters: Double = 5_000.0,
        val popularityCap: Long = 100,
        val activityCap: Long = 50,
        val taxRefundMultiplier: Double = 0.8,
        /** 면세 태그 판정 패턴. 언어별 표기가 다르다(T6 실측: EN·JA·ZH·KO 네 가지). */
        val taxRefundTitlePatterns: List<String> = listOf("[Tax Refund Shop]"),
    )

    data class Weights(
        val distance: Double = 0.4,
        val popularity: Double = 0.25,
        val activity: Double = 0.15,
        val affinity: Double = 0.2,
        /** 출처가 정해지기 전이라 0이다(T4). */
        val trend: Double = 0.0,
    )

    /** 재배열(R13~R16). */
    data class Dpp(
        val alpha: Double = 1.0,
        val sigma: Double = 0.5,
        /** 창 k. */
        val window: Int = 12,
        /** 후보 상한 N. */
        val candidateCap: Int = 300,
        /** 묶음 크기 B. */
        val batchSize: Int = 200,
        val sameCategoryDistance: Double = 0.3,
        val differentCategoryDistance: Double = 1.0,
    )

    /** 커서(R4). */
    data class Cursor(
        val maxAge: Duration = Duration.ofHours(24),
    )

    /** 묶음 캐시(R17). 캐시라 값이 틀려도 결과는 같다. */
    data class Cache(
        val ttl: Duration = Duration.ofMinutes(30),
    )
}
