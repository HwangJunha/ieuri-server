package org.around.ieuri.tourism.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 시군구 대표 위치 선정 설정(SPEC-0015 · V46, POL-044).
 *
 * @param cellSizeDegrees 밀집 격자 한 칸의 크기(도). 0.02도는 위도 기준 약 2km다.
 * @param minSpotCount 가장 밀집한 격자의 최소 KO spot 수(전 언어 행이 아니라 KO 기준). 미달이면 선정 실패(NULL)로 추천에서 빠진다.
 */
@ConfigurationProperties(prefix = "ieuri.district-representative")
data class DistrictRepresentativeProperties(
    val cellSizeDegrees: Double = 0.02,
    val minSpotCount: Int = 10,
)
