package org.around.ieuri.tourism.application.footprint

import org.around.ieuri.tourism.application.footprint.provided.dto.MapCellView
import org.around.ieuri.tourism.domain.footprint.MapCell
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 칸 도메인 모델 → 읽기 모델(SPEC-0030).
 * - 조회와 뮤테이션이 같은 변환을 써야 클라이언트가 캐시를 고칠 수 있다.
 */
internal fun MapCell.toView(locale: DomainLocale, visitedDistrictCodes: Set<String>): MapCellView = MapCellView(
    id = id,
    districtCodes = districtCodes.sorted(),
    name = name.resolve(locale),
    provinceName = provinceName.resolve(locale),
    provinceCode = provinceCode,
    center = center,
    viewerVisited = isVisitedBy(visitedDistrictCodes),
)
