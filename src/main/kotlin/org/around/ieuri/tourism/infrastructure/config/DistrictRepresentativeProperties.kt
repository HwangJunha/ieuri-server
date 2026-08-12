package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 시군구 대표 위치 선정 설정(SPEC-0015 · V46). 두 값 모두 **실측으로 고른 잠정값이라 설정으로
 * 둔다**(POL-044) — 상수로 박으면 데이터가 늘었을 때 재배포 없이 조정할 수 없다.
 *
 * 실측(2026-08-11, cell=0.02 · min=10 · KO 한정): 252개 시군구 중 226개 선정 · 26개 NULL.
 * 영덕군의 center-최근접 spot 거리가 5842m → 219m로 줄었고, 대표 위치가 자기 시군구 밖으로
 * 나간 건은 0건이다.
 *
 * **[minSpotCount]는 KO 행 기준이다.** spot은 언어마다 다른 행이라 전 언어를 세면 같은 장소가
 * 최대 4번 잡힌다 — 같은 문턱이라도 번역 커버리지에 따라 의미가 달라진다(전 언어 기준 20은
 * KO 기준 20과 완전히 다른 기준이다). 문턱을 조정할 때 이 점을 잊으면 안 된다.
 *
 * @param cellSizeDegrees 밀집 격자 한 칸의 크기(도). 0.02도는 위도 기준 약 2km다. 키우면 더
 *   넓게 뭉쳐 선정률이 오르지만 대표 위치가 밀집 중심에서 멀어진다.
 * @param minSpotCount 가장 밀집한 격자의 최소 KO spot 수. 미달이면 그 시군구는 선정 실패(NULL)로
 *   두어 추천에서 빠진다 — 갈 곳이 없는 지역을 추천하지 않기 위한 문턱이다.
 */
@ConfigurationProperties(prefix = "ieuri.district-representative")
data class DistrictRepresentativeProperties(
    val cellSizeDegrees: Double = 0.02,
    val minSpotCount: Int = 10,
)
