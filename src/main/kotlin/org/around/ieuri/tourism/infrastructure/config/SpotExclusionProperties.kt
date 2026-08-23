package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 비관광 spot 제외 판정 설정(SPEC-0018 · POL-044). 값은 `application.yaml`에 있으며 오탐이
 * 발견되면 배포 없이 교정한다 — 그래서 코드가 아니라 설정이다.
 *
 * @param keywords 제외 키워드. **빈 목록은 "전부 제외"가 아니라 "아무것도 제외하지 않음"이다**(R12).
 * @param branchNamePatterns 지점명 보호 패턴. 병원·의원에 입점한 소매점이 업태 명칭을 지점명으로
 *   갖는 경우를 되살린다(예: 올리브영 제주대학병원점).
 */
@ConfigurationProperties(prefix = "ieuri.tourism.spot-exclusion")
data class SpotExclusionProperties(
    val keywords: List<String> = emptyList(),
    val branchNamePatterns: List<String> = emptyList(),
)
