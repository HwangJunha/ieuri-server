package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCell

/**
 * 칸 도메인 모델 → 읽기 모델(SPEC-0030).
 * - 조회와 뮤테이션이 같은 변환을 써야 클라이언트가 캐시를 고칠 수 있다.
 */
fun MapCell.toView(locale: DomainLocale, visitedDistrictCodes: Set<String>): MapCellView = MapCellView(
    id = id,
    districtCodes = districtCodes.sorted(),
    name = name.resolve(locale),
    provinceName = provinceName.resolve(locale),
    provinceCode = provinceCode,
    center = center,
    viewerVisited = isVisitedBy(visitedDistrictCodes),
)
